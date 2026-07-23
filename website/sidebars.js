module.exports = {
  docs: [
    {
      type: 'category',
      label: 'Difflicious',
      items: [
        'index',
        'quickstart',
        'basic-differs',
        'derivation',
        'configuring-differs',
        'cli',
        {
          type: 'category',
          label: 'Library Integrations',
          link: {
            type: 'doc',
            id: 'library-integrations',
          },
          items: [
            'library-integrations/munit',
            'library-integrations/scalatest',
            'library-integrations/weaver',
            'library-integrations/cats',
            'library-integrations/circe',
          ],
        },
        {
          type: 'category',
          label: 'Build Tool Integrations',
          items: [
            'sbt-plugin',
          ],
        },
        'best-practices-and-faq',
        'cheatsheet',
      ],
    },
  ],
};
