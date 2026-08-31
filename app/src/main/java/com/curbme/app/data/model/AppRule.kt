package com.curbme.app.data.model

/**
 * Why we made this file:
 * This is a "Domain Model" or "POJO" (Plain Old Java Object). In clean architecture,
 * we separate how data is stored (Database Entities) from how the business logic
 * uses it.
 * 
 * This file represents a single app on the child's phone and the specific
 * restrictions applied to it—such as whether it's fully blocked, if it has
 * a daily time limit, or if it's a system app that should never be restricted.
 * 
 * What the file name defines:
 * "AppRule" defines the specific logic and constraints (the "Rules")
 * governing the usage of an application.
 */
class AppRule
/**
 * Full constructor to initialize the immutable app rule.
 */(// Getters for immutability
    val packageName: String?,
    val appName: String?,
    val isBlocked: Boolean,
    val isBlockShorts: Boolean,
    val dailyLimitMinutes: Int,
    val allowedTimeWindowStart: Int,
    val allowedTimeWindowEnd: Int,
    val isSystemApp: Boolean
) 