package com.outpost

class ModuleGraphSpec {
    static final Set<String> PROJECT_PATHS = [
        ':platform-sanity:static-data-model',
        ':platform-sanity:domain-architecture',
        ':platform-sanity:static-data-check',
        ':platform-sanity:static-data-repository',
        ':common-iso:repository',
        ':common-iso:domain',
        ':payment:common:repository',
        ':payment:common:domain',
        ':framework:persistence',
        ':framework:logging',
        ':framework:security',
        ':framework:healthprobe',
        ':framework:queue-processor',
        ':account:repository',
        ':account:domain',
        ':merchant-configuration:repository',
        ':merchant-configuration:domain',
        ':accounting:domain',
        ':accounting:repository',
        ':accounting:report',
        ':accounting:api',
        ':accounting:api-client',
        ':tax:domain',
        ':tax:repository',
        ':fx:domain',
        ':fx:repository',
        ':payment:domain',
        ':payment:repository',
        ':psp-integration:domain',
        ':psp-integration:client',
        ':gateway-api',
        ':ledger-api',
        ':static-data-job'
    ] as Set

    static final Map<String, Set<String>> PROJECT_DEPENDENCIES = [
        ':platform-sanity:static-data-model': [],
        ':platform-sanity:domain-architecture': [
            ':platform-sanity:static-data-model',
            ':common-iso:domain',
            ':payment:common:domain',
            ':account:domain',
            ':merchant-configuration:domain',
            ':accounting:domain',
            ':tax:domain',
            ':fx:domain',
            ':payment:domain'
        ],
        ':platform-sanity:static-data-check': [
            ':platform-sanity:static-data-repository',
            ':platform-sanity:static-data-model',
            ':common-iso:repository',
            ':payment:common:repository',
            ':account:repository',
            ':merchant-configuration:repository',
            ':accounting:repository',
            ':payment:repository',
            ':common-iso:domain',
            ':payment:common:domain',
            ':account:domain',
            ':merchant-configuration:domain'
        ],
        ':platform-sanity:static-data-repository': [],
        ':common-iso:repository': [
            ':common-iso:domain',
            ':platform-sanity:static-data-repository',
            ':framework:persistence'
        ],
        ':payment:common:repository': [
            ':payment:common:domain',
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
            ':common-iso:domain',
            ':payment:common:domain',
            ':account:domain',
            ':platform-sanity:static-data-repository',
            ':framework:persistence'
        ],
        ':common-iso:domain': [
            ':platform-sanity:static-data-model'
        ],
        ':payment:common:domain': [
            ':platform-sanity:static-data-model',
            ':common-iso:domain'
        ],
        ':framework:persistence': [
            ':account:domain',
            ':common-iso:domain',
            ':accounting:domain',
            ':merchant-configuration:domain',
            ':payment:common:domain'
        ],
        ':framework:logging': [],
        ':framework:security': [],
        ':framework:healthprobe': [],
        ':framework:queue-processor': [':framework:logging'],
        ':account:domain': [
            ':platform-sanity:static-data-model',
            ':common-iso:domain'
        ],
        ':merchant-configuration:domain': [
            ':platform-sanity:static-data-model',
            ':common-iso:domain',
            ':payment:common:domain',
            ':account:domain'
        ],
        ':accounting:domain': [
            ':platform-sanity:static-data-model',
            ':common-iso:domain',
            ':payment:common:domain',
            ':account:domain',
            ':merchant-configuration:domain',
            ':tax:domain',
            ':fx:domain'
        ],
        ':accounting:repository': [
            ':accounting:domain',
            ':account:domain',
            ':common-iso:domain',
            ':payment:common:domain',
            ':platform-sanity:static-data-repository',
            ':framework:persistence'
        ],
        ':accounting:report': [],
        ':accounting:api': [
            ':accounting:report',
            ':common-iso:domain',
            ':payment:common:domain',
            ':framework:logging'
        ],
        ':accounting:api-client': [
            ':accounting:api',
            ':framework:security'
        ],
        ':tax:domain': [
            ':platform-sanity:static-data-model',
            ':common-iso:domain',
            ':payment:common:domain'
        ],
        ':fx:domain': [
            ':platform-sanity:static-data-model',
            ':common-iso:domain'
        ],
        ':tax:repository': [
            ':tax:domain',
            ':common-iso:domain',
            ':payment:common:domain',
            ':framework:persistence'
        ],
        ':fx:repository': [
            ':fx:domain',
            ':common-iso:domain',
            ':framework:persistence'
        ],
        ':payment:domain': [
            ':platform-sanity:static-data-model',
            ':account:domain',
            ':common-iso:domain',
            ':payment:common:domain',
            ':tax:domain',
            ':psp-integration:domain'
        ],
        ':payment:repository': [
            ':payment:domain',
            ':account:domain',
            ':common-iso:domain',
            ':payment:common:domain',
            ':platform-sanity:static-data-repository',
            ':framework:persistence'
        ],
        ':psp-integration:domain': [
            ':platform-sanity:static-data-model',
            ':common-iso:domain',
            ':payment:common:domain'
        ],
        ':psp-integration:client': [
            ':psp-integration:domain',
            ':framework:persistence',
            ':account:domain',
            ':common-iso:domain',
            ':payment:common:domain'
        ],
        ':gateway-api': [
            ':platform-sanity:static-data-check',
            ':common-iso:domain',
            ':common-iso:repository',
            ':payment:common:domain',
            ':payment:common:repository',
            ':framework:logging',
            ':framework:persistence',
            ':framework:security',
            ':framework:healthprobe',
            ':framework:queue-processor',
            ':account:domain',
            ':account:repository',
            ':merchant-configuration:domain',
            ':merchant-configuration:repository',
            ':tax:domain',
            ':tax:repository',
            ':payment:domain',
            ':payment:repository',
            ':psp-integration:domain',
            ':psp-integration:client',
            ':accounting:report',
            ':accounting:api',
            ':accounting:api-client'
        ],
        ':ledger-api': [
            ':platform-sanity:static-data-check',
            ':account:repository',
            ':merchant-configuration:repository',
            ':accounting:repository',
            ':common-iso:domain',
            ':payment:common:domain',
            ':framework:logging',
            ':framework:persistence',
            ':framework:security',
            ':framework:healthprobe',
            ':framework:queue-processor',
            ':account:domain',
            ':merchant-configuration:domain',
            ':accounting:domain',
            ':tax:domain',
            ':fx:domain',
            ':fx:repository',
            ':accounting:report',
            ':accounting:api',
            ':accounting:api-client'
        ],
        ':static-data-job': [
            ':platform-sanity:static-data-check',
            ':platform-sanity:static-data-repository',
            ':common-iso:repository',
            ':common-iso:domain',
            ':payment:common:repository',
            ':payment:common:domain',
            ':framework:persistence',
            ':account:repository',
            ':account:domain',
            ':merchant-configuration:repository',
            ':merchant-configuration:domain',
            ':accounting:domain',
            ':accounting:repository',
            ':tax:domain',
            ':fx:domain',
            ':payment:domain',
            ':payment:repository',
            ':psp-integration:domain'
        ]
    ].collectEntries { projectPath, dependencies ->
        [(projectPath): dependencies as Set]
    }.asUnmodifiable()

    static final Set<String> DOMAIN_PROJECT_PATHS = [
        ':common-iso:domain',
        ':payment:common:domain',
        ':account:domain',
        ':merchant-configuration:domain',
        ':accounting:domain',
        ':tax:domain',
        ':fx:domain',
        ':payment:domain',
        ':psp-integration:domain'
    ] as Set

    static final Set<String> DEPLOYABLE_PROJECT_PATHS = [
        ':gateway-api',
        ':ledger-api',
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
