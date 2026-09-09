package com.outpost

class ModuleGraphSpec {
    static final Set<String> PROJECT_PATHS = [
        ':platform-sanity:static-data-model',
        ':platform-sanity:static-data-check',
        ':platform-sanity:static-data-repository',
        ':common-iso-static-data',
        ':common-iso',
        ':common-payment-static-data',
        ':common-payment',
        ':common-persistence',
        ':account-configuration-static-data',
        ':account-configuration',
        ':merchant-configuration-static-data',
        ':merchant-configuration',
        ':accounting',
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
        ':platform-sanity:static-data-check': [
            ':platform-sanity:static-data-repository'
            ,':platform-sanity:static-data-model'
            ,':common-iso-static-data'
            ,':common-payment-static-data'
            ,':account-configuration-static-data'
            ,':merchant-configuration-static-data'
            ,':common-iso'
            ,':common-payment'
            ,':account-configuration'
            ,':merchant-configuration'
        ],
        ':platform-sanity:static-data-repository': [],
        ':common-iso-static-data': [
            ':common-iso',
            ':platform-sanity:static-data-repository',
            ':common-persistence'
        ],
        ':common-payment-static-data': [
            ':common-payment',
            ':platform-sanity:static-data-repository',
            ':common-persistence'
        ],
        ':account-configuration-static-data': [
            ':account-configuration',
            ':platform-sanity:static-data-repository',
            ':common-persistence'
        ],
        ':merchant-configuration-static-data': [
            ':merchant-configuration',
            ':platform-sanity:static-data-repository',
            ':common-persistence'
        ],
        ':common-iso': [
            ':platform-sanity:static-data-model'
        ],
        ':common-payment': [
            ':platform-sanity:static-data-model',
            ':common-iso'
        ],
        ':common-persistence': [],
        ':account-configuration': [
            ':platform-sanity:static-data-model',
            ':common-iso'
        ],
        ':merchant-configuration': [
            ':platform-sanity:static-data-model',
            ':common-iso',
            ':common-payment',
            ':account-configuration'
        ],
        ':accounting': [
            ':platform-sanity:static-data-model',
            ':common-iso',
            ':common-payment',
            ':account-configuration',
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
        ':gateway-api': [
            ':platform-sanity:static-data-check',
            ':common-iso',
            ':common-payment',
            ':common-persistence',
            ':account-configuration',
            ':merchant-configuration',
            ':tax',
            ':payment',
            ':psp-integration'
        ],
        ':ledger-api': [
            ':platform-sanity:static-data-check',
            ':common-iso',
            ':common-payment',
            ':common-persistence',
            ':account-configuration',
            ':merchant-configuration',
            ':accounting',
            ':tax',
            ':fx'
        ],
        ':outpost-worker': [
            ':platform-sanity:static-data-check',
            ':common-iso',
            ':common-persistence',
            ':account-configuration',
            ':merchant-configuration',
            ':payment',
            ':psp-integration'
        ],
        ':static-data-job': [
            ':platform-sanity:static-data-check',
            ':platform-sanity:static-data-repository',
            ':common-iso-static-data',
            ':common-iso',
            ':common-payment-static-data',
            ':common-payment',
            ':common-persistence',
            ':account-configuration-static-data',
            ':account-configuration',
            ':merchant-configuration-static-data',
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
        ':gateway-api',
        ':ledger-api',
        ':outpost-worker',
        ':static-data-job'
    ] as Set

    static final Set<String> PERSISTENCE_FRAMEWORK_PATHS = [
        ':common-persistence'
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
