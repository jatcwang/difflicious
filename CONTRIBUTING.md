# Building the documentation site

`sbt --client docs/docusaurusCreateSite`

## Iterating on the site

After having run `docusaurusCreateSite` once (to setup e.g. dependencies), you can

Start docusaurus dev server, which should then be accessible on http://localhost:3000/difflicious/
```
cd website && npm start
```

Then run mdoc in watch mode. Then you can iterate on the docs and see changes reflected in the site.
```
sbt --client docs/mdoc --watch
``

