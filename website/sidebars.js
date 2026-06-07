module.exports = {
  docs: [
    {
      type: 'category',
      label: 'Difflicious',
      items: [
        'index',
        'introduction',
        'quickstart',
        'derivation',
        'basic-differs',
        'configuring-differs',
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
        'best-practices-and-faq',
        'cheatsheet',
      ],
    },
  ],
};
