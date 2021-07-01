package com.ignite.utils

import grails.core.GrailsClass
import grails.util.Holders
import org.grails.core.DefaultGrailsDomainClassProperty
import org.grails.datastore.gorm.GormEntity

trait CloneTrait<D> extends GormEntity<D> {

    D clone() {

        def domainInstanceToClone = this

        //TODO: PRECISA ENTENDER ISSO! MB-249 no youtrack
        //Algumas classes chegam aqui com nome da classe + _$$_javassist_XX
        if (domainInstanceToClone.getClass().name.contains("_javassist"))
            return null

        //Our target instance for the instance we want to clone
        // recursion
        def newDomainInstance = domainInstanceToClone.getClass().newInstance()

        //Returns a DefaultGrailsDomainClass (as interface GrailsDomainClass) for inspecting properties
        GrailsClass domainClass = Holders.getGrailsApplication().getDomainClass(newDomainInstance.getClass().name)
        //domainInstanceToClone.domainClass.grailsApplication.getDomainClass(newDomainInstance.getClass().name)

        def notCloneable = domainClass.getPropertyValue("notCloneable")

        for(DefaultGrailsDomainClassProperty prop in domainClass?.getPersistentProperties()) {
            if (notCloneable && prop.name in notCloneable)
                continue

            if (prop.association) {

                if (prop.owningSide) {
                    //we have to deep clone owned associations
                    if (prop.oneToOne) {
                        def newAssociationInstance = domainInstanceToClone?."${prop.name}" instanceof CloneTrait ? domainInstanceToClone?."${prop.name}".clone() : null
                        newDomainInstance."${prop.name}" = newAssociationInstance
                    } else {

                        domainInstanceToClone."${prop.name}".each { associationInstance ->
                            def newAssociationInstance = associationInstance instanceof CloneTrait ? associationInstance.clone() : null

                            if (newAssociationInstance)
                                newDomainInstance."addTo${prop.name.capitalize()}"(newAssociationInstance)
                        }
                    }
                } else {

                    if (!prop.bidirectional) {

                        //If the association isn't owned or the owner, then we can just do a  shallow copy of the reference.
                        newDomainInstance."${prop.name}" = domainInstanceToClone."${prop.name}"
                    }
                    // @@JR
                    // Yes bidirectional and not owning. E.g. clone Report, belongsTo Organisation which hasMany
                    // manyToOne. Just add to the owning objects collection.
                    else {
                        //println "${prop.owningSide} - ${prop.name} - ${prop.oneToMany}"
                        //return
                        if (prop.manyToOne) {

                            newDomainInstance."${prop.name}" = domainInstanceToClone."${prop.name}"
                            def owningInstance = domainInstanceToClone."${prop.name}"
                            // Need to find the collection.
                            String otherSide = prop.otherSide.name.capitalize()
                            //println otherSide
                            //owningInstance."addTo${otherSide}"(newDomainInstance)
                        }
                        else if (prop.manyToMany) {
                            //newDomainInstance."${prop.name}" = [] as Set

                            domainInstanceToClone."${prop.name}".each {

                                //newDomainInstance."${prop.name}".add(it)
                            }
                        }

                        else if (prop.oneToMany) {
                            domainInstanceToClone."${prop.name}".each { associationInstance ->
                                def newAssociationInstance = associationInstance instanceof CloneTrait ? associationInstance.clone() : null
                                newDomainInstance."addTo${prop.name.capitalize()}"(newAssociationInstance)
                            }
                        }
                    }
                }
            } else {
                //If the property isn't an association then simply copy the value
                newDomainInstance."${prop.name}" = domainInstanceToClone."${prop.name}"

                if (prop.name == "dateCreated" || prop.name == "lastUpdated") {
                    newDomainInstance."${prop.name}" = null
                }
            }
        }

        return newDomainInstance
    }
}
