---
mapped_pages:
  - https://www.elastic.co/guide/en/elasticsearch/plugins/current/example-text-analysis-plugin.html
---

# Example text analysis plugin [example-text-analysis-plugin]

This example shows how to create a simple "Hello world" text analysis plugin using the stable plugin API. The plugin provides a custom Lucene token filter that strips all tokens except for "hello" and "world".

Elastic provides a Grade plugin, `elasticsearch.stable-esplugin`, that makes it easier to develop and package stable plugins. The steps in this guide assume you use this plugin. However, you don’t need Gradle to create plugins.

1. Create a new directory for your project.
2. In this example, the source code is organized under the `main` and `test` directories. In your project’s home directory, create `src/` `src/main/`, and `src/test/` directories.
3. Create the following `build.gradle` build script in your project’s home directory:

    ```gradle
    ext.pluginApiVersion = '8.7.0'
    ext.luceneVersion = '9.5.0'

    buildscript {
      ext.pluginApiVersion = '8.7.0'
      repositories {
        mavenCentral()
      }
      dependencies {
        classpath "org.elasticsearch.gradle:build-tools:${pluginApiVersion}"
      }
    }

    apply plugin: 'elasticsearch.stable-esplugin'
    apply plugin: 'elasticsearch.yaml-rest-test'

    esplugin {
      name 'my-plugin'
      description 'My analysis plugin'
    }

    group 'org.example'
    version '1.0-SNAPSHOT'

    repositories {
      mavenLocal()
      mavenCentral()
    }

    dependencies {

      //TODO transitive dependency off and plugin-api dependency?
      compileOnly "org.elasticsearch.plugin:elasticsearch-plugin-api:${pluginApiVersion}"
      compileOnly "org.elasticsearch.plugin:elasticsearch-plugin-analysis-api:${pluginApiVersion}"
      compileOnly "org.apache.lucene:lucene-analysis-common:${luceneVersion}"

      //TODO for testing this also have to be declared
      testImplementation "org.elasticsearch.plugin:elasticsearch-plugin-api:${pluginApiVersion}"
      testImplementation "org.elasticsearch.plugin:elasticsearch-plugin-analysis-api:${pluginApiVersion}"
      testImplementation "org.apache.lucene:lucene-analysis-common:${luceneVersion}"

      testImplementation ('junit:junit:4.13.2'){
        exclude group: 'org.hamcrest'
      }
      testImplementation 'org.mockito:mockito-core:4.4.0'
      testImplementation 'org.hamcrest:hamcrest:2.2'

    }
    ```

4. In `src/main/java/org/example/`, create `HelloWorldTokenFilter.java`. This file provides the code for a token filter that strips all tokens except for "hello" and "world":

    ```java
    package org.example;

    import org.apache.lucene.analysis.FilteringTokenFilter;
    import org.apache.lucene.analysis.TokenStream;
    import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

    import java.util.Arrays;

    public class HelloWorldTokenFilter extends FilteringTokenFilter {
        private final CharTermAttribute term = addAttribute(CharTermAttribute.class);

        public HelloWorldTokenFilter(TokenStream input) {
            super(input);
        }

        @Override
        public boolean accept() {
            if (term.length() != 5) return false;
            return Arrays.equals(term.buffer(), 0, 4, "hello".toCharArray(), 0, 4)
                    || Arrays.equals(term.buffer(), 0, 4, "world".toCharArray(), 0, 4);
        }
    }
    ```

5. This filter can be provided to Elasticsearch using the following `HelloWorldTokenFilterFactory.java` factory class. The `@NamedComponent` annotation is used to give the filter the `hello_world` name. This is the name you can use to refer to the filter, once the plugin has been deployed.

    ```java
    package org.example;

    import org.apache.lucene.analysis.TokenStream;
    import org.elasticsearch.plugin.analysis.TokenFilterFactory;
    import org.elasticsearch.plugin.NamedComponent;

    @NamedComponent(value = "hello_world")
    public class HelloWorldTokenFilterFactory implements TokenFilterFactory {

        @Override
        public TokenStream create(TokenStream tokenStream) {
            return new HelloWorldTokenFilter(tokenStream);
        }

    }
    ```

6. Unit tests may go under the `src/test` directory. You will have to add dependencies for your preferred testing framework.
7. Run:

    ```sh
    gradle bundlePlugin
    ```

    This builds the JAR file, generates the metadata files, and bundles them into a plugin ZIP file. The resulting ZIP file will be written to the `build/distributions` directory.

8. [Install the plugin](/reference/elasticsearch-plugins/plugin-management.md).
9. You can use the `_analyze` API to verify that the `hello_world` token filter works as expected:

    ```console
    GET /_analyze
    {
      "text": "hello to everyone except the world",
      "tokenizer": "standard",
      "filter":  ["hello_world"]
    }
    ```



## YAML REST tests [_yaml_rest_tests_2]

If you are using the `elasticsearch.stable-esplugin` plugin for Gradle, you can use {{es}}'s YAML Rest Test framework. This framework allows you to load your plugin in a running test cluster and issue real REST API queries against it. The full syntax for this framework is beyond the scope of this tutorial, but there are many examples in the Elasticsearch repository. Refer to the [example analysis plugin](https://github.com/elastic/elasticsearch/tree/main/plugins/examples/stable-analysis) in the {{es}} Github repository for an example.

1. Create a `yamlRestTest` directory in the `src` directory.
2. Under the `yamlRestTest` directory, create a `java` folder for Java sources and a `resources` folder.
3. In `src/yamlRestTest/java/org/example/`, create `HelloWorldPluginClientYamlTestSuiteIT.java`. This class implements `ESClientYamlSuiteTestCase`.

    ```java
    import com.carrotsearch.randomizedtesting.annotations.Name;
    import com.carrotsearch.randomizedtesting.annotations.ParametersFactory;
    import org.elasticsearch.test.rest.yaml.ClientYamlTestCandidate;
    import org.elasticsearch.test.rest.yaml.ESClientYamlSuiteTestCase;

    public class HelloWorldPluginClientYamlTestSuiteIT extends ESClientYamlSuiteTestCase {

        public HelloWorldPluginClientYamlTestSuiteIT(
                @Name("yaml") ClientYamlTestCandidate testCandidate
        ) {
            super(testCandidate);
        }

        @ParametersFactory
        public static Iterable<Object[]> parameters() throws Exception {
            return ESClientYamlSuiteTestCase.createParameters();
        }
    }
    ```

4. In `src/yamlRestTest/resources/rest-api-spec/test/plugin`, create the `10_token_filter.yml` YAML file:

    ```yaml
    ## Sample rest test
    ---
    "Hello world plugin test - removes all tokens except hello and world":
      - do:
          indices.analyze:
            body:
              text: hello to everyone except the world
              tokenizer: standard
              filter:
                - type: "hello_world"
      - length: { tokens: 2 }
      - match:  { tokens.0.token: "hello" }
      - match:  { tokens.1.token: "world" }
    ```

5. Run the test with:

    ```sh
    gradle yamlRestTest
    ```


