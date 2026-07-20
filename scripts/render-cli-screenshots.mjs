#!/usr/bin/env node

import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const snapshotRoot = path.join(repositoryRoot, "modules/cli/src/test/resources/snapshot");
const assetRoot = path.join(repositoryRoot, "docs/docs/assets");
const temporaryRoot = fs.mkdtempSync(path.join(os.tmpdir(), "difflicious-cli-screenshots-"));

const browserCandidates = [
  process.env.CHROME_BIN,
  "chromium",
  "chromium-browser",
  "google-chrome",
  "google-chrome-stable",
].filter(Boolean);

const browser = browserCandidates.find((candidate) => {
  const result = spawnSync(candidate, ["--version"], { stdio: "ignore" });
  return result.status === 0;
});

if (!browser) {
  throw new Error("Could not find Chromium or Chrome. Set CHROME_BIN to the browser executable.");
}

const escapeHtml = (value) =>
  value.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;");

const ansiToHtml = (value) => {
  const foregroundColors = {
    31: "#ff7b72",
    32: "#7ee787",
    35: "#f778ba",
    37: "#b1bac4",
  };
  const ansiPattern = /\x1b\[([0-9;]*)m/g;
  let foreground = null;
  let background = null;
  let cursor = 0;
  let html = "";

  const append = (text) => {
    if (!text) return;

    const styles = [];
    if (foreground) styles.push(`color:${foreground}`);
    if (background) styles.push(`background:${background}`, "color:#0d1117");

    const escaped = escapeHtml(text);
    html += styles.length ? `<span style="${styles.join(";")}">${escaped}</span>` : escaped;
  };

  for (const match of value.matchAll(ansiPattern)) {
    append(value.slice(cursor, match.index));

    const codes = (match[1] || "0").split(";").map(Number);
    for (const code of codes) {
      if (code === 0) {
        foreground = null;
        background = null;
      } else if (code === 39) {
        foreground = null;
      } else if (code === 49) {
        background = null;
      } else if (foregroundColors[code]) {
        foreground = foregroundColors[code];
      } else if (code === 43) {
        background = "#d29922";
      }
    }

    cursor = match.index + match[0].length;
  }

  append(value.slice(cursor));
  return html;
};

const readSnapshot = (relativePath) => fs.readFileSync(path.join(snapshotRoot, relativePath), "utf8");

const readSnapshotLines = (relativePath, lineCount) =>
  readSnapshot(relativePath).split("\n").slice(0, lineCount);

const ansi = (code, value) => `\x1b[${code}m${value}\x1b[39m`;

const shortTypeName = (typeName) =>
  typeName.withTypeParamsLong.replace(
    /(?:[A-Za-z_$][\w$]*\.)+([A-Za-z_$][\w$]*)/g,
    "$1",
  );

const summaryLabel = (result) => {
  if (result.kind === "record" || result.kind === "list" || result.kind === "map") {
    return shortTypeName(result.typeName);
  }
  if (result.kind === "mismatch_type") {
    return `${shortTypeName(result.obtainedTypeName)} != ${shortTypeName(result.expectedTypeName)}`;
  }
  if (result.pairType === "obtained_only") return `+ ${result.obtained}`;
  if (result.pairType === "expected_only") return `- ${result.expected}`;
  return result.isSame ? result.obtained : `${result.obtained} -> ${result.expected}`;
};

const buildTree = (result, label, id = []) => {
  let children = [];

  if (!result.isIgnored && result.kind === "record") {
    const fieldWidth = Math.max(0, ...result.fields.map(({ name }) => name.length));
    children = result.fields.map(({ name, value }) =>
      buildTree(value, `${name.padEnd(fieldWidth)}: ${summaryLabel(value)}`, [...id, name]),
    );
  } else if (!result.isIgnored && result.kind === "list") {
    children = result.items.map((item, index) =>
      buildTree(item, `[${index}]: ${summaryLabel(item)}`, [...id, `[${index}]`]),
    );
  }

  return { result, label, id, children };
};

const nodeKey = (node) => node.id.join("/");

const isDifference = (node) =>
  !node.result.isIgnored &&
  !node.result.isOk &&
  (node.children.length === 0 || node.result.pairType !== "both");

const firstDifference = (node) => {
  if (isDifference(node)) return node;
  for (const child of node.children) {
    const difference = firstDifference(child);
    if (difference) return difference;
  }
  return null;
};

const visibleRows = (node, expanded, depth = 0) => {
  const isExpanded = expanded.has(nodeKey(node));
  const row = { node, depth, isExpanded };
  return isExpanded
    ? [row, ...node.children.flatMap((child) => visibleRows(child, expanded, depth + 1))]
    : [row];
};

const rowColor = (result) => {
  if (result.isIgnored) return 37;
  if (result.pairType === "obtained_only") return 31;
  if (result.pairType === "expected_only") return 32;
  if (!result.isOk) return 35;
  return null;
};

const renderTreeRow = ({ node, depth, isExpanded }, selected) => {
  const indent = "  ".repeat(depth);
  const indicator =
    node.children.length === 0 ? (depth > 0 ? "  " : "") : isExpanded ? "▾ " : "▸ ";
  const plain = `${indent}${indicator}${node.label}`;
  let styled;

  if (
    node.result.kind === "value" &&
    node.result.pairType === "both" &&
    !node.result.isSame &&
    !node.result.isIgnored
  ) {
    const summary = `${node.result.obtained} -> ${node.result.expected}`;
    const summaryStart = plain.lastIndexOf(summary);
    styled = [
      ansi(35, plain.slice(0, summaryStart)),
      ansi(31, node.result.obtained),
      ansi(35, " -> "),
      ansi(32, node.result.expected),
    ].join("");
  } else {
    const color = rowColor(node.result);
    styled = color ? ansi(color, plain) : plain;
  }

  const padded = `${styled}${" ".repeat(Math.max(0, 96 - plain.length))}`;
  return selected ? `> ${padded} <` : `  ${padded}  `;
};

const borderedPanel = (lines) => [
  `┌${"─".repeat(98)}┐`,
  ...lines.map((line) => `│ ${line.padEnd(96)} │`),
  `└${"─".repeat(98)}┘`,
];

const orderDetailsLines = () => {
  const reportLines = readSnapshot("RendererSpec/plain-listener-report.snap").split("\n");
  const test = reportLines.find((line) => line.startsWith("Failure 1: ")).slice("Failure 1: ".length);
  const location = path.basename(
    reportLines.find((line) => line.startsWith("Location: ")).slice("Location: ".length),
  );
  const result = JSON.parse(readSnapshot("RendererSpec/json.snap")).tree;
  const tree = buildTree(result, summaryLabel(result));
  const selected = firstDifference(tree);
  const expanded = new Set(["", "lines"]);

  for (let index = 1; index < selected.id.length; index += 1) {
    expanded.add(selected.id.slice(0, index).join("/"));
  }

  const rows = visibleRows(tree, expanded);
  return [
    ...borderedPanel([`Test: ${test}`, `Location: ${location}`]),
    ...rows.map((row) => renderTreeRow(row, nodeKey(row.node) === nodeKey(selected))),
  ];
};

const renderScreenshot = ({ name, lines, height }) => {
  const width = 882;
  const deviceScaleFactor = 2;
  const renderedLines = lines.map(ansiToHtml).join("\n");
  const html = `<!doctype html>
<html>
<head>
  <meta charset="utf-8">
  <style>
    * { box-sizing: border-box; }
    html, body { margin: 0; width: ${width}px; height: ${height}px; overflow: hidden; }
    body {
      background: #0d1117;
      color: #c9d1d9;
      font: 14px/20px "Noto Sans Mono", "DejaVu Sans Mono", monospace;
    }
    pre {
      margin: 0;
      padding: 20px 20px 18px;
      white-space: pre;
      font: inherit;
      font-variant-ligatures: none;
    }
  </style>
</head>
<body><pre>${renderedLines}</pre></body>
</html>`;

  const htmlPath = path.join(temporaryRoot, `${name}.html`);
  const outputPath = path.join(assetRoot, `${name}.png`);
  fs.writeFileSync(htmlPath, html);

  const result = spawnSync(
    browser,
    [
      "--headless",
      "--disable-gpu",
      "--no-sandbox",
      "--hide-scrollbars",
      `--force-device-scale-factor=${deviceScaleFactor}`,
      `--window-size=${width},${height}`,
      `--screenshot=${outputPath}`,
      pathToFileURL(htmlPath).href,
    ],
    { stdio: "inherit" },
  );

  if (result.status !== 0) throw new Error(`Failed to render ${name}.png`);
  process.stdout.write(`${path.relative(repositoryRoot, outputPath)}\n`);
};

try {
  renderScreenshot({
    name: "cli-search",
    height: 398,
    lines: readSnapshotLines(
      "InteractiveReportViewerSpec/test finder - fuzzily highlights displayed test names/01-highlight-second.snap",
      18,
    ),
  });

  const detailsLines = orderDetailsLines();
  renderScreenshot({
    name: "cli-details",
    height: 38 + detailsLines.length * 20,
    lines: detailsLines,
  });
} finally {
  fs.rmSync(temporaryRoot, { recursive: true, force: true });
}
