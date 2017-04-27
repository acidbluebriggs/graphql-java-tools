package com.coxautodev.graphql.tools

import graphql.language.InputValueDefinition
import graphql.schema.DataFetchingEnvironment
import org.slf4j.LoggerFactory
import ru.vyarus.java.generics.resolver.GenericsResolver
import java.lang.reflect.Method

abstract class Resolver(val resolver: GraphQLResolver<*>) {

    companion object {
        val log = LoggerFactory.getLogger(Resolver::class.java)

        @JvmStatic fun create(dataClassType: Class<*>): Resolver = NoResolver(dataClassType)

        @JvmStatic fun create(resolver: GraphQLResolver<*>): Resolver {

            val resolverType = resolver.javaClass

            // Grab the parent interface with type GraphQLResolver from our resolver and get its first type argument.
            val dataClassType = GenericsResolver.resolve(resolverType).type(GraphQLResolver::class.java)?.genericTypes()?.first()

            if(dataClassType == null || dataClassType !is Class<*>) {
                throw ResolverError("Unable to determine data class for resolver '${resolverType.name}' from generic interface!  This is most likely a bug with graphql-java-tools.")
            }

            return if(dataClassType != Void::class.java) DataClassResolver(resolver, dataClassType) else RootResolver(resolver)
        }
    }

    val resolverType = resolver.javaClass

    private fun isBoolean(returnType: Class<*>): Boolean {
        return returnType.isAssignableFrom(Boolean::class.java) || returnType.isPrimitive && returnType.javaClass.name == "boolean"
    }

    abstract protected fun getMethod(name: String, arguments: List<InputValueDefinition>): GetMethodResult

    fun findMethod(name: String, arguments: List<InputValueDefinition>): FindMethodResult {
        val result = getMethod(name, arguments)
        return FindMethodResult(this, name, arguments, result.method, result.methodClass, result.resolverMethod)
    }

    protected fun getMethod(clazz: Class<*>, name: String, argumentCount: Int): Method? {
        val methods = clazz.methods
        // Check for the following one by one:
        //   1. Method with exact field name
        //   2. Method that returns a boolean with "is" style getter
        //   3. Method with "get" style getter
        return methods.find {
            it.name == name && validateArgumentCount(clazz, it, argumentCount)
        } ?: methods.find {
            (isBoolean(it.returnType) && it.name == "is${name.capitalize()}") && validateArgumentCount(clazz, it, argumentCount)
        } ?: methods.find {
            it.name == "get${name.capitalize()}" && validateArgumentCount(clazz, it, argumentCount)
        }
    }

    private fun validateArgumentCount(clazz: Class<*>, method: Method, argumentCount: Int): Boolean {
        if(method.parameterCount == argumentCount) {
            return true
        }

        if(method.parameterCount == (argumentCount + 1)) {
            if(method.parameterTypes.last() is DataFetchingEnvironment) {
                return true
            } else {
                log.warn("Found candidate resolver method ${clazz.name}#${method.name}, but its extra (last) parameter is not of type ${DataFetchingEnvironment::class.java.name}!")
            }
        }

        return false
    }

    protected fun getMethodMissingStart(name: String, argumentCount: Int): String {
        val extraArgumentCount = argumentCount + 1
        return "No method found named '$name' with $argumentCount argument${if(argumentCount != 1) "s" else ""} (or $extraArgumentCount argument${if(extraArgumentCount != 1) "s" else ""} with last parameter of type ${DataFetchingEnvironment::class.java})"
    }

    protected data class GetMethodResult(
        val method: Method,
        val methodClass: Class<*>,
        val resolverMethod: Boolean
    )
    data class FindMethodResult(
        val resolver: Resolver,
        val name: String,
        val arguments: List<InputValueDefinition>,
        val method: Method,
        val methodClass: Class<*>,
        val resolverMethod: Boolean) {

        fun normalizeIndex(index: Int): Int = if(resolverMethod && resolver is DataClassResolver) index + 1 else index
    }
    class NoopResolver: GraphQLRootResolver
}

class RootResolver(resolver: GraphQLResolver<*>): Resolver(resolver) {

    override fun getMethod(name: String, arguments: List<InputValueDefinition>): GetMethodResult {
        val method = getMethod(resolverType, name, arguments.size) ?: throw ResolverError("${getMethodMissingStart(name, arguments.size)} on resolver ${resolverType.name}")
        return GetMethodResult(method, resolverType, true)
    }
}

class DataClassResolver(resolver: GraphQLResolver<*>, val dataClassType: Class<*>): Resolver(resolver) {

    override fun getMethod(name: String, arguments: List<InputValueDefinition>): GetMethodResult {
        var method = getMethod(resolverType, name, arguments.size + 1)
        if(method != null) {
            return GetMethodResult(method, resolverType, true)
        }

        method = getMethod(dataClassType, name, arguments.size) ?: throw ResolverError("${getMethodMissingStart(name, arguments.size)} on resolver ${resolverType.name} or its data class ${dataClassType.name}")
        return GetMethodResult(method, dataClassType, false)
    }
}

class NoResolver(val dataClassType: Class<*>): Resolver(NoopResolver()) {

    override fun getMethod(name: String, arguments: List<InputValueDefinition>): GetMethodResult {
        val method = getMethod(dataClassType, name, arguments.size) ?: throw ResolverError("${getMethodMissingStart(name, arguments.size)} on data class ${dataClassType.name}")
        return GetMethodResult(method, dataClassType, false)
    }
}

