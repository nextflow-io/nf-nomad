# nf-nomad plugin

This plugin implements a Nextflow executor for [Hashicorp Nomad](https://www.nomadproject.io).


# Maintainers

Please note that this is a **community contributed** plugin and is a collaboration between 

1. Abhinav Sharma (@abhi18av) as part of his PhD work at the Stellenbosch University and Jorge Aguilera (@jagedn) from Evaluacion y desarrollo de negocios, Spain.
2. Tomas (@tomiles) and his team from Center For Medical Genetics Ghent, Belgium.

The contribution roles during the development of initial plugin and testing along with the long term commitments have been discussed in [development and infrastructure group](https://github.com/nextflow-io/nf-nomad/issues/2#issue-1703543034).

Feel free to reach out to us on the `#platform-nomad` channel on Slack for discussions and feedbacks.

## Plugin Assets

- `settings.gradle`

    Gradle project settings.

- `plugins/nf-nomad`

    The plugin implementation base directory.

- `plugins/nf-nomad/build.gradle`

    Plugin Gradle build file. Project dependencies should be added here.

- `plugins/nf-nomad/src/resources/META-INF/MANIFEST.MF`

    Manifest file defining the plugin attributes e.g. name, version, etc. The attribute `Plugin-Class` declares the plugin main class. This class should extend the base class `nextflow.plugin.BasePlugin` e.g. `nextflow.Nomad.NomadPlugin`.

- `plugins/nf-nomad/src/resources/META-INF/extensions.idx`

    This file declares one or more extension classes provided by the plugin. Each line should contain the fully qualified name of a Java class that implements the `org.pf4j.ExtensionPoint` interface (or a sub-interface).

- `plugins/nf-nomad/src/main`

    The plugin implementation sources.

- `plugins/nf-nomad/src/test`

    The plugin unit tests.

## `ExtensionPoint`s

`ExtensionPoint` is the basic interface which uses nextflow-core to integrate plugins into it. It's only a basic interface and serves as starting point for more specialized extensions.

## Unit testing

Run the following command in the project root directory (ie. where the file `settings.gradle` is located):

```bash
./gradlew check
```

## Testing and debugging

To run and test the plugin in a development environment, configure a local Nextflow build with the following steps:

1. Clone the Nextflow repository in your computer into a sibling directory:

    ```bash
    git clone --depth 1 https://github.com/nextflow-io/nextflow _resources/nextflow
    ```

2. Generate the nextflow class path

    ```bash
    cd _resources/nextflow && ./gradlew exportClasspath
    ```

3. Compile the plugin alongside the Nextflow code:

    ```bash
    cd ../../ && ./gradlew compileGroovy
    ```

4. Run Nextflow with the plugin, using `./launch.sh` as a drop-in replacement for the `nextflow` command, and adding the option `-plugins nf-nomad` to load the plugin:

    ```bash
    ./launch.sh run main.nf -plugins nf-nomad
    ```

## Package, upload and publish

The project should be hosted in a GitHub repository whose name should match the name of the plugin, that is the name of the directory in the `plugins` folder (e.g. `nf-nomad`).

Follow these steps to package, upload and publish the plugin:

1. Create a file named `gradle.properties` in the project root containing the following attributes (this file should not be committed to Git):

   - `github_organization`: the GitHub organisation where the plugin repository is hosted.
   - `github_username`: The GitHub username granting access to the plugin repository.
   - `github_access_token`: The GitHub access token required to upload and commit changes to the plugin repository.
   - `github_commit_email`: The email address associated with your GitHub account.

2. Use the following command to package and create a release for your plugin on GitHub:

    ```bash
    ./gradlew :plugins:nf-nomad:upload
    ```

3. Create a pull request against [nextflow-io/plugins](https://github.com/nextflow-io/plugins/blob/main/plugins.json) to make the plugin accessible to Nextflow.
