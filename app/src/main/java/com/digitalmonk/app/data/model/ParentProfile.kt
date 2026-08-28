package com.digitalmonk.app.data.model

/**
 * Why we made this file:
 * This is another "Domain Model" (POJO). In an app like Digital Monk, we need
 * a structured way to represent the parent or guardian who controls the
 * dashboard. This class holds their basic identity information (like a unique ID
 * and their display name) so it can be passed around the UI or synced with
 * a remote database like Firebase or MongoDB.
 * 
 * What the file name defines:
 * "Parent" specifies the role of the user this data represents.
 * "Profile" indicates that this class holds account-related identity information.
 */
class ParentProfile {
    // Getters for immutability
    val id: String?
    val name: String?

    /**
     * Default constructor.
     * In Kotlin, you used default parameters (id = "", name = "").
     * In Java, we achieve this by using "Constructor Overloading" — creating
     * a constructor with no arguments that sets the default values.
     */
    constructor() {
        this.id = ""
        this.name = ""
    }

    /**
     * Full constructor to initialize the profile with specific data.
     */
    constructor(id: String?, name: String?) {
        this.id = id
        this.name = name
    }
}