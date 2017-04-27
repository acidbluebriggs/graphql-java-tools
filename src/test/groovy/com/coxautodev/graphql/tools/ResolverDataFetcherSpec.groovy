package com.coxautodev.graphql.tools

import graphql.language.InputValueDefinition
import graphql.language.NonNullType
import graphql.language.TypeName
import graphql.schema.DataFetchingEnvironment
import graphql.schema.DataFetchingEnvironmentImpl
import spock.lang.Specification

/**
 * @author Andrew Potter
 */
class ResolverDataFetcherSpec extends Specification {

    def "data fetcher throws exception if resolver has too many arguments"() {
        when:
            createResolver("active", new GraphQLRootResolver() {
                boolean active(def arg1, def arg2) { true }
            })

        then:
            thrown(ResolverError)
    }

    def "data fetcher throws exception if resolver has too few arguments"() {
        when:
            createResolver("active", [new InputValueDefinition("doesNotExist")], new GraphQLRootResolver() {
                boolean active() { true }
            })

        then:
            thrown(ResolverError)
    }

    def "data fetcher prioritizes methods on the resolver"() {
        setup:
            def name = "Resolver Name"
            def resolver = createResolver("name", new GraphQLResolver<DataClass>() {
                String getName(DataClass dataClass) { name }
            })

        expect:
            resolver.get(createEnvironment(new DataClass())) == name
    }

    def "data fetcher uses data class methods if no resolver method is given"() {
        setup:
            def resolver = createResolver("name", new GraphQLResolver<DataClass>() {})

        expect:
            resolver.get(createEnvironment(new DataClass())) == DataClass.name
    }

    def "data fetcher prioritizes methods without a prefix"() {
        setup:
            def name = "correct name"
            def resolver = createResolver("name", new GraphQLResolver<DataClass>() {
                String name(DataClass dataClass) { name }

                String getName(DataClass dataClass) { "in" + name }
            })

        expect:
            resolver.get(createEnvironment(new DataClass())) == name
    }

    def "data fetcher uses 'is' prefix for booleans (primitive type)"() {
        setup:
            def resolver = createResolver("active", new GraphQLResolver<DataClass>() {
                boolean isActive(DataClass dataClass) { true }

                boolean getActive(DataClass dataClass) { true }
            })

        expect:
            resolver.get(createEnvironment(new DataClass()))
    }

    def "data fetcher uses 'is' prefix for Booleans (Object type)"() {
        setup:
            def resolver = createResolver("active", new GraphQLResolver<DataClass>() {
                Boolean isActive(DataClass dataClass) { Boolean.TRUE }

                Boolean getActive(DataClass dataClass) { Boolean.TRUE }
            })

        expect:
            resolver.get(createEnvironment(new DataClass()))
    }

    def "data fetcher passes environment if method has extra argument"() {
        setup:
            def resolver = createResolver("active", new GraphQLResolver<DataClass>() {
                boolean isActive(DataClass dataClass, DataFetchingEnvironment env) {
                    env instanceof DataFetchingEnvironment
                }
            })

        expect:
            resolver.get(createEnvironment(new DataClass()))
    }

    def "data fetcher marshalls input object if required"() {
        setup:
            def name = "correct name"
            def resolver = createResolver("active", [new InputValueDefinition("input")], new GraphQLRootResolver() {
                boolean active(InputClass input) {
                    input instanceof InputClass && input.name == name
                }
            })

        expect:
            resolver.get(createEnvironment([input: [name: name]]))
    }

    def "data fetcher doesn't marshall input object if not required"() {
        setup:
            def name = "correct name"
            def resolver = createResolver("active", [new InputValueDefinition("input")], new GraphQLRootResolver() {
                boolean active(Map input) {
                    input instanceof Map && input.name == name
                }
            })

        expect:
            resolver.get(createEnvironment([input: [name: name]]))
    }

    def "data fetcher returns null if nullable argument is passed null"() {
        setup:
            def resolver = createResolver("echo", [new InputValueDefinition("message", new TypeName("String"))], new GraphQLRootResolver() {
                String echo(String message) {
                    return message
                }
            })

        expect:
            resolver.get(createEnvironment()) == null
    }

    def "data fetcher throws exception if non-null argument is passed null"() {
        setup:
            def resolver = createResolver("echo", [new InputValueDefinition("message", new NonNullType(new TypeName("String")))], new GraphQLRootResolver() {
                String echo(String message) {
                    return message
                }
            })

        when:
            resolver.get(createEnvironment())

        then:
            thrown(ResolverError)
    }

    private static ResolverDataFetcher createResolver(String methodName, List<InputValueDefinition> arguments = [], GraphQLResolver<?> resolver) {
        ResolverDataFetcher.create(Resolver.create(resolver).findMethod(methodName, arguments))
    }

    private static DataFetchingEnvironment createEnvironment(Map<String, Object> arguments = [:]) {
        createEnvironment(new Object(), arguments)
    }

    private static DataFetchingEnvironment createEnvironment(Object source, Map<String, Object> arguments = [:]) {
        new DataFetchingEnvironmentImpl(source, arguments, null, null, null, null, null)
    }
}

class DataClass {
    private static final String name = "Data Class Name"

    String getName() {
        name
    }
}

class InputClass {
    String name
}
