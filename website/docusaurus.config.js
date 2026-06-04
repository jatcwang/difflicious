const {themes} = require('prism-react-renderer');

/** @type {import('@docusaurus/types').Config} */
const config = {
  title: 'Difflicious',
  tagline: 'Readable, configurable diffs for Scala tests',
  favicon: 'img/difflicious.svg',
  url: 'https://jatcwang.github.io',
  baseUrl: '/difflicious/',
  organizationName: 'jatcwang',
  projectName: 'difflicious',
  trailingSlash: false,
  onBrokenLinks: 'throw',
  markdown: {
    hooks: {
      onBrokenMarkdownLinks: 'warn',
    },
  },

  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  presets: [
    [
      'classic',
      {
        docs: {
          path: '../docs/target/jvm-2.13/mdoc',
          routeBasePath: 'docs',
          sidebarPath: require.resolve('./sidebars.js'),
          editUrl: ({docPath}) =>
            `https://github.com/jatcwang/difflicious/edit/main/docs/docs/${docPath}`,
        },
        blog: false,
        theme: {
          customCss: require.resolve('./src/css/custom.css'),
        },
      },
    ],
  ],

  themeConfig: {
    image: 'img/difflicious.svg',
    navbar: {
      title: 'Difflicious',
      logo: {
        alt: 'Difflicious logo',
        src: 'img/difflicious.svg',
      },
      items: [
        {
          type: 'docSidebar',
          sidebarId: 'docs',
          position: 'left',
          label: 'Docs',
        },
        {
          href: 'https://github.com/jatcwang/difflicious',
          label: 'GitHub',
          position: 'right',
        },
      ],
    },
    footer: {
      style: 'dark',
      links: [
        {
          title: 'Docs',
          items: [
            {label: 'Quickstart', to: '/docs/quickstart'},
            {label: 'Introduction', to: '/docs/introduction'},
            {label: 'Configuring Differs', to: '/docs/configuring-differs'},
          ],
        },
        {
          title: 'More',
          items: [
            {
              label: 'GitHub',
              href: 'https://github.com/jatcwang/difflicious',
            },
          ],
        },
      ],
      copyright: `Copyright (c) ${new Date().getFullYear()} Difflicious contributors`,
    },
    prism: {
      theme: themes.github,
      darkTheme: themes.dracula,
      defaultLanguage: 'scala',
      additionalLanguages: ['java', 'scala'],
    },
  },
};

module.exports = config;
