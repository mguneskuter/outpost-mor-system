package com.outpost

class ModuleGraphSpec {
    static final Set<String> PROJECT_PATHS = [
        ':platform-sanity:static-data-model',
        ':platform-sanity:static-data-check',
        ':common-iso',
        ':common-payment',
        ':account-configuration',
        ':merchant-configuration',
        ':accounting',
        ':tax',
        ':fx',
        ':payment',
        ':psp-integration',
        ':outpost-api',
        ':outpost-ledger-service',
        ':outpost-worker',
        ':seed-job'
    ] as Set

    static final Map<String, Set<String>> PROJECT_DEPENDENCIES = [
        ':platform-sanity:static-data-model': [],
        ':platform-sanity:static-data-check': [
            ':platform-sanity:static-data-model'
        ],
        ':common-iso': [
            ':platform-sanity:static-data-model'
        ],
        ':common-payment': [
            ':platform-sanity:static-data-model',
            ':common-iso'
        ],
        ':account-configuration': [
            ':platform-sanity:static-data-model',
            ':common-iso'
        ],
        ':merchant-configuration': [
            ':platform-sanity:static-data-model',
            ':common-iso'
        ],
        ':accounting': [
            ':platform-sanity:static-data-model',
            ':common-iso',
            ':merchant-configuration',
            ':tax',
            ':fx'
        ],
        ':tax': [
            ':platform-sanity:static-data-model',
            ':common-iso',
            ':common-payment'
        ],
        ':fx': [
            ':platform-sanity:static-data-model',
            ':common-iso'
        ],
        ':payment': [
            ':platform-sanity:static-data-model',
            ':common-iso',
            ':common-payment',
            ':tax',
            ':psp-integration'
        ],
        ':psp-integration': [
            ':platform-sanity:static-data-model',
            ':common-iso'
        ],
        ':outpost-api': [
            ':platform-sanity:static-data-check',
            ':common-iso',
            ':common-payment',
            ':account-configuration',
            ':merchant-configuration',
            ':tax',
            ':payment',
            ':psp-integration'
        ],
        ':outpost-ledger-service': [
            ':platform-sanity:static-data-check',
            ':common-iso',
            ':common-payment',
            ':account-configuration',
            ':merchant-configuration',
            ':accounting',
            ':tax',
            ':fx'
        ],
        ':outpost-worker': [
            ':platform-sanity:static-data-check',
            ':common-iso',
            ':account-configuration',
            ':merchant-configuration',
            ':payment',
            ':psp-integration'
        ],
        ':seed-job': [
            ':platform-sanity:static-data-check',
            ':common-iso',
            ':common-payment',
            ':account-configuration',
            ':merchant-configuration',
            ':accounting',
            ':tax',
            ':fx',
            ':payment',
            ':psp-integration'
        ]
    ].collectEntries { projectPath, dependencies ->
        [(projectPath): dependencies as Set]
    }.asUnmodifiable()

    static final Set<String> DOMAIN_PROJECT_PATHS = [
        ':common-iso',
        ':common-payment',
        ':account-configuration',
        ':merchant-configuration',
        ':accounting',
        ':tax',
        ':fx',
        ':payment',
        ':psp-integration'
    ] as Set

    static final Set<String> DEPLOYABLE_PROJECT_PATHS = [
        ':outpost-api',
        ':outpost-ledger-service',
        ':outpost-worker',
        ':seed-job'
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
