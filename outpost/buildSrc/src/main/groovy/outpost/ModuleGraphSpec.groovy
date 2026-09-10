package com.outpost

class ModuleGraphSpec {
    static final Set<String> PROJECT_PATHS = [
        ':platform-sanity:static-data-model',
        ':platform-sanity:domain-architecture',
        ':platform-sanity:static-data-check',
        ':platform-sanity:static-data-repository',
        ':common-iso:repository',
        ':common-iso:domain',
        ':common-payment:repository',
        ':common-payment:domain',
        ':framework:persistence',
        ':framework:logging',
        ':framework:security',
        ':account:repository',
        ':account:domain',
        ':merchant-configuration:repository',
        ':merchant-configuration:domain',
        ':accounting:domain',
        ':accounting:persistence',
        ':tax',
        ':fx',
        ':payment',
        ':psp-integration',
        ':gateway-api',
        ':ledger-api',
        ':outpost-worker',
        ':static-data-job'
    ] as Set

    static final Map<String, Set<String>> PROJECT_DEPENDENCIES = [
        ':platform-sanity:static-data-model': [],
        ':platform-sanity:domain-architecture': [
            ':platform-sanity:static-data-model',
            ':common-iso:domain',
            ':common-payment:domain',
            ':account:domain',
            ':merchant-configuration:domain',
            ':accounting:domain',
            ':tax'
        ],
        ':platform-sanity:static-data-check': [
            ':platform-sanity:static-data-repository',
            ':platform-sanity:static-data-model',
            ':common-iso:repository',
            ':common-payment:repository',
            ':account:repository',
            ':merchant-configuration:repository',
            ':accounting:persistence',
            ':common-iso:domain',
            ':common-payment:domain',
            ':account:domain',
            ':merchant-configuration:domain'
        ],
        ':platform-sanity:static-data-repository': [],
        ':common-iso:repository': [
            ':common-iso:domain',
            ':platform-sanity:static-data-repository',
            ':framework:persistence'
        ],
        ':common-payment:repository': [
            ':common-payment:domain',
            ':platform-sanity:static-data-repository',
            ':framework:persistence'
        ],
        ':account:repository': [
            ':account:domain',
            ':platform-sanity:static-data-repository',
            ':framework:persistence'
        ],
        ':merchant-configuration:repository': [
            ':merchant-configuration:domain',
            ':platform-sanity:static-data-repository',
            ':framework:persistence'
        ],
        ':common-iso:domain': [
            ':platform-sanity:static-data-model'
        ],
        ':common-payment:domain': [
            ':platform-sanity:static-data-model',
            ':common-iso:domain'
        ],
        ':framework:persistence': [],
        ':framework:logging': [],
        ':framework:security': [],
        ':account:domain': [
            ':platform-sanity:static-data-model',
            ':common-iso:domain'
        ],
        ':merchant-configuration:domain': [
            ':platform-sanity:static-data-model',
            ':common-iso:domain',
            ':common-payment:domain',
            ':account:domain'
        ],
        ':accounting:domain': [
            ':platform-sanity:static-data-model',
            ':common-iso:domain',
            ':common-payment:domain',
            ':account:domain',
            ':merchant-configuration:domain',
            ':tax',
            ':fx'
        ],
        ':accounting:persistence': [
            ':accounting:domain',
            ':platform-sanity:static-data-repository',
            ':framework:persistence'
        ],
        ':tax': [
            ':platform-sanity:static-data-model',
            ':common-iso:domain',
            ':common-payment:domain'
        ],
        ':fx': [
            ':platform-sanity:static-data-model',
            ':common-iso:domain'
        ],
        ':payment': [
            ':platform-sanity:static-data-model',
            ':common-iso:domain',
            ':common-payment:domain',
            ':tax',
            ':psp-integration'
        ],
        ':psp-integration': [
            ':platform-sanity:static-data-model',
            ':common-iso:domain'
        ],
        ':gateway-api': [
            ':platform-sanity:static-data-check',
            ':common-iso:domain',
            ':common-payment:domain',
            ':framework:persistence',
            ':account:domain',
            ':merchant-configuration:domain',
            ':tax',
            ':payment',
            ':psp-integration'
        ],
        ':ledger-api': [
            ':platform-sanity:static-data-check',
            ':accounting:persistence',
            ':common-iso:domain',
            ':common-payment:domain',
            ':framework:persistence',
            ':account:domain',
            ':merchant-configuration:domain',
            ':accounting:domain',
            ':tax',
            ':fx'
        ],
        ':outpost-worker': [
            ':platform-sanity:static-data-check',
            ':common-iso:domain',
            ':framework:persistence',
            ':account:domain',
            ':merchant-configuration:domain',
            ':payment',
            ':psp-integration'
        ],
        ':static-data-job': [
            ':platform-sanity:static-data-check',
            ':platform-sanity:static-data-repository',
            ':common-iso:repository',
            ':common-iso:domain',
            ':common-payment:repository',
            ':common-payment:domain',
            ':framework:persistence',
            ':account:repository',
            ':account:domain',
            ':merchant-configuration:repository',
            ':merchant-configuration:domain',
            ':accounting:domain',
            ':accounting:persistence',
            ':tax',
            ':fx',
            ':payment',
            ':psp-integration'
        ]
    ].collectEntries { projectPath, dependencies ->
        [(projectPath): dependencies as Set]
    }.asUnmodifiable()

    static final Set<String> DOMAIN_PROJECT_PATHS = [
        ':common-iso:domain',
        ':common-payment:domain',
        ':account:domain',
        ':merchant-configuration:domain',
        ':accounting:domain',
        ':tax',
        ':fx',
        ':payment',
        ':psp-integration'
    ] as Set

    static final Set<String> DEPLOYABLE_PROJECT_PATHS = [
        ':gateway-api',
        ':ledger-api',
        ':outpost-worker',
        ':static-data-job'
    ] as Set

    static final Set<String> PERSISTENCE_FRAMEWORK_PATHS = [
        ':framework:persistence'
    ] as Set

    static Set<String> expectedEdges() {
        PROJECT_DEPENDENCIES.collectMany { projectPath, dependencies ->
            dependencies.collect { dependencyPath -> edge(projectPath, dependencyPath) }
        } as Set
    }

    static String edge(String projectPath, String dependencyPath) {
        "${projectPath} -> ${dependencyPath}"
    }
}
