import clsx from 'clsx';
import Heading from '@theme/Heading';
import Layout from '@theme/Layout';
import Link from '@docusaurus/Link';
import useBaseUrl from '@docusaurus/useBaseUrl';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import styles from './index.module.css';

function Feature({title, children}) {
  return (
    <section className={styles.feature}>
      <Heading as="h2">{title}</Heading>
      <p>{children}</p>
    </section>
  );
}

export default function Home() {
  const {siteConfig} = useDocusaurusContext();
  const logoUrl = useBaseUrl('/img/difflicious.svg');

  return (
    <Layout title={siteConfig.title} description={siteConfig.tagline}>
      <header className={styles.hero}>
        <div className={clsx('container', styles.heroInner)}>
          <img
            className={styles.logo}
            src={logoUrl}
            alt="Difflicious logo"
          />
          <Heading as="h1" className={styles.title}>
            {siteConfig.title}
          </Heading>
          <p className={styles.tagline}>{siteConfig.tagline}</p>
          <div className={styles.actions}>
            <Link className="button button--secondary button--lg" to="/docs/quickstart">
              Quick Start
            </Link>
          </div>
        </div>
      </header>
      <main>
        <div className={clsx('container', styles.features)}>
          <Feature title="Pinpoint exactly what's different">
            Quickly pinpoint the field, collection item, or map entry that caused
            an assertion to fail.
          </Feature>
          <Feature title="Configurable comparison">
            Exclude fields from comparison and configure alternative pairing strategy when comparing collections.
          </Feature>
          <Feature title="Test framework and library integration">
            Integrates with popular framework and libraries such as MUnit, ScalaTest, Weaver, Cats, and Circe.
          </Feature>
        </div>
      </main>
    </Layout>
  );
}
