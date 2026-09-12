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
        ':accounting:queue-api',
        ':tax',
        ':fx',
        ':payment:domain',
        ':payment:repository',
        ':psp-integration:domain',
        ':psp-integration:client',
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
            ':tax',
            ':payment:domain'
        ],
        ':platform-sanity:static-data-check': [
            ':platform-sanity:static-data-repository',
            ':platform-sanity:static-data-model',
            ':common-iso:repository',
            ':common-payment:repository',
            ':account:repository',
            ':merchant-configuration:repository',
            ':accounting:persistence',
            ':payment:repository',
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
        ':framework:persistence': [
            ':account:domain'
        ],
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
            ':account:domain',
            ':platform-sanity:static-data-repository',
            ':framework:persistence'
        ],
        ':accounting:queue-api': [
            ':platform-sanity:static-data-model',
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
        ':payment:domain': [
            ':platform-sanity:static-data-model',
            ':common-iso:domain',
            ':common-payment:domain',
            ':tax',
            ':psp-integration:domain'
        ],
        ':payment:repository': [
            ':payment:domain', ':platform-sanity:static-data-repository', ':framework:persistence'
        ],
        ':psp-integration:domain': [
            ':platform-sanity:static-data-model',
            ':common-iso:domain',
            ':common-payment:domain'
        ],
        ':psp-integration:client': [
            ':psp-integration:domain',
            ':framework:persistence',
            ':account:domain',
            ':common-iso:domain',
            ':common-payment:domain'
        ],
        ':gateway-api': [
            ':platform-sanity:static-data-check',
            ':common-iso:domain',
            ':common-iso:repository',
            ':common-payment:domain',
            ':common-payment:repository',
            ':framework:persistence',
            ':framework:security',
            ':account:domain',
            ':account:repository',
            ':merchant-configuration:domain',
            ':merchant-configuration:repository',
            ':tax',
            ':payment:domain',
            ':psp-integration:domain',
            ':psp-integration:client'
        ],
        ':ledger-api': [
            ':platform-sanity:static-data-check',
            ':accounting:persistence',
            ':common-iso:domain',
            ':common-payment:domain',
            ':framework:persistence',
            ':framework:security',
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
            ':payment:domain',
            ':psp-integration:domain',
            ':psp-integration:client'
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
            ':accounting:queue-api',
            ':tax',
            ':fx',
            ':payment:domain',
            ':payment:repository',
            ':psp-integration:domain'
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
        ':payment:domain',
        ':psp-integration:domain'
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
