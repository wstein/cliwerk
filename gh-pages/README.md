# gh-pages source

`index.html` here is the source of truth for the landing page published at
<https://wstein.github.io/cliwerk/>. The release workflow (`.github/workflows/release.yml`)
copies it onto the `gh-pages` branch on every release, alongside the flat Maven repo, generated
javadoc, and the picocli-spec JSON Schema.

Edit this file (not the `gh-pages` branch directly) to change the landing page.
