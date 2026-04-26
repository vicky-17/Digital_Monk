package com.example.digitalmonk.data.repository;

import com.example.digitalmonk.data.model.AppRule;
import java.util.List;

/**
 * Why we made this file:
 * In the "Clean Architecture" pattern, a Repository acts as a mediator between
 * the data sources (like your local Room Database or the remote Vercel/MongoDB backend)
 * and the rest of the app (like your ViewModels).
 *
 * We use an interface here to define a "Contract". It tells the app WHAT operations
 * can be done (e.g., getting all rules, updating a rule) without revealing HOW
 * they are done.
 *
 * What the file name defines:
 * "AppRule" signifies the specific data model this repository manages.
 * "Repository" is the architectural term for a data-access coordinator.
 */
public interface AppRuleRepository {

    // I have added a few example method signatures that you will likely
    // need to implement later when you connect this to your local database.

    // List<AppRule> getAllRules();
    // AppRule getRuleForPackage(String packageName);
    // void saveRule(AppRule rule);
}