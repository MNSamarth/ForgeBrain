/**
 * Persistence contracts for {@link com.forgebrain.backend.entities}. Declared as plain
 * interfaces rather than extending a Spring Data {@code CrudRepository}, since no Spring Data
 * Firestore dependency is declared in pom.xml yet (see docs/CONFIGURATION.md Section 2 and
 * TODO.md) — once that dependency is added, these are expected to extend Spring Data's
 * repository interfaces directly rather than being reimplemented from scratch.
 */
package com.forgebrain.backend.repositories;
