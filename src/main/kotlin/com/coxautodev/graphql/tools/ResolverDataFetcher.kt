package com.coxautodev.graphql.tools

import com.esotericsoftware.reflectasm.MethodAccess
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import graphql.language.NonNullType
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import java.lang.reflect.Method

class ResolverDataFetcher(val sourceResolver: SourceResolver, method: Method, val args: List<ArgumentPlaceholder>): DataFetcher<Any> {

    companion object {
        val mapper = ObjectMapper().registerKotlinModule()

        @JvmStatic fun create(findMethodResult: Resolver.FindMethodResult): ResolverDataFetcher {

            val (resolver, name, argumentDefinitions, method, methodClass, isResolverMethod) = findMethodResult
            val args = mutableListOf<ArgumentPlaceholder>()

            // Add source argument if this is a resolver (but not a root resolver)
            if(isResolverMethod && resolver is DataClassResolver) {
                val expectedType = resolver.dataClassType
                args.add({ environment ->
                    val source = environment.getSource<Any>()
                    if (expectedType != source.javaClass) {
                        throw ResolverError("Source type (${source.javaClass.name}) is not expected type (${expectedType.name})!")
                    }

                    source
                })
            }

            // Add an argument for each argument defined in the GraphQL schema
            val methodParameters = method.parameterTypes
            argumentDefinitions.forEach { definition ->
                args.add({ environment ->
                    val value = environment.arguments[definition.name] ?: if(definition.type is NonNullType) {
                        throw ResolverError("Missing required argument with name '$name', this is most likely a bug with graphql-java-tools")
                    } else {
                        return@add null
                    }

                    // Convert to specific type if actual argument value is Map<?, ?> and method parameter type is not Map<?, ?>
                    if (value is Map<*, *>) {
                        val methodParameterIndex = args.size // Get the index of the next parameter since list.nextIndex == (list.size + 1) and we always add an argument for each parameter.
                        val type = methodParameters[methodParameterIndex] ?: throw ResolverError("Missing method type at position $methodParameterIndex, this is most likely a bug with graphql-java-tools")
                        if (!Map::class.java.isAssignableFrom(type)) {
                            return@add mapper.convertValue(value, type)
                        }
                    }

                    value
                })
            }

            // Add DataFetchingEnvironment argument
            if(method.parameterCount - args.size == 1) {
                if(!DataFetchingEnvironment::class.java.isAssignableFrom(methodParameters.last()!!)) {
                    throw ResolverError("Method '${method.name}' of class '${methodClass.name}' has an extra parameter, but the last parameter is not of type ${DataFetchingEnvironment::class.java.name}!  This is most likely a bug with graphql-java-tools")
                }
                args.add({ environment -> environment })
            } else if(method.parameterCount - args.size != 0) {
                throw ResolverError("Method '${method.name}' of class '${methodClass.name}' had an unexpected argument cound, expected ${argumentDefinitions.size} or ${argumentDefinitions.size + 1} but found ${method.parameterCount}!  This is most likely a bug with graphql-java-tools")
            }

            // Add source resolver depending on whether or not this is a resolver method
            val sourceResolver: SourceResolver = if(isResolverMethod) ({ resolver.resolver }) else ({ environment ->
                val source = environment.getSource<Any>()
                if(!methodClass.isAssignableFrom(source.javaClass)) {
                    throw ResolverError("Expected source object to be an instance of '${methodClass.name}' but instead got '${source.javaClass.name}'")
                }

                source
            })

            return ResolverDataFetcher(sourceResolver, method, args)
        }
    }

    // Convert to reflactasm reflection
    val methodAccess = MethodAccess.get(method.declaringClass)!!
    val methodIndex = methodAccess.getIndex(method.name, *method.parameterTypes)

    override fun get(environment: DataFetchingEnvironment): Any? {
        val source = sourceResolver(environment)
        val args = this.args.map { it(environment) }.toTypedArray()
        return methodAccess.invoke(source, methodIndex, *args)
    }
}

class ResolverError(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
typealias SourceResolver = (DataFetchingEnvironment) -> Any
typealias ArgumentPlaceholder = (DataFetchingEnvironment) -> Any?
