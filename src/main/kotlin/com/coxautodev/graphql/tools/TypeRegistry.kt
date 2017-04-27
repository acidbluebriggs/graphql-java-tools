package com.coxautodev.graphql.tools

import com.google.common.collect.HashBiMap
import graphql.language.InterfaceTypeDefinition
import graphql.language.TypeName
import graphql.language.UnionTypeDefinition
import graphql.schema.GraphQLTypeReference
import java.lang.reflect.Method

/**
 * @author Andrew Potter
 */
open class TypeRegistry(private val interfaceDefinitions: Map<String, InterfaceTypeDefinition>, val unionDefinitions: Map<String, UnionTypeDefinition>) {

    private val objects = HashBiMap.create<String, RegisteredType>()

    @Synchronized fun registerType(typeName: TypeName, type: Class<*>, method: Resolver.FindMethodResult, description: String): GraphQLTypeReference {
        val name = typeName.name

        if(!interfaceDefinitions.containsKey(name) && !unionDefinitions.containsKey(name)) {
            objects.getOrPut(name, { RegisteredType(name, type) }).addReference(type, method, description)
        }

        return GraphQLTypeReference(name)
    }

    fun forMethod(method: Resolver.FindMethodResult, description: String) = ForMethod(this, method, description)

    class ForMethod(val typeRegistry: TypeRegistry, val method: Resolver.FindMethodResult, val description: String) {
        fun registerType(typeName: TypeName, type: Class<*>): GraphQLTypeReference {
            return typeRegistry.registerType(typeName, type, method, description)
        }
    }

    private data class RegisteredType(val name: String, val type: Class<*>) {

        val references = mutableListOf<Reference>()

        fun addReference(type: Class<*>, method: Resolver.FindMethodResult, description: String) {
            if(this.type != type) {
                TODO("Real error here")
            }

            references.add(Reference(method.methodClass, method.method, description))
        }
    }

    private data class Reference(val clazz: Class<*>, val method: Method, val description: String)
}
