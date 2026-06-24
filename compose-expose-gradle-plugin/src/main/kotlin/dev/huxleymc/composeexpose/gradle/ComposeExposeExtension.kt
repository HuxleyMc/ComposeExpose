package dev.huxleymc.composeexpose.gradle

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

abstract class ComposeExposeExtension @Inject constructor(objects: ObjectFactory) {
    val moduleName: Property<String> = objects.property(String::class.java)
    val sourceSet: Property<String> = objects.property(String::class.java).convention("main")
    val sourceRoots: ListProperty<String> = objects.listProperty(String::class.java)
}
