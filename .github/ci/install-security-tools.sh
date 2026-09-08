#!/usr/bin/env bash

set -euo pipefail

TRUFFLEHOG_VERSION="3.97.4"
GITLEAKS_VERSION="8.30.1"

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
binary_directory="${repository_root}/bin"

sha256_file() {
    local file_path="$1"

    if command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "${file_path}" | awk '{print $1}'
        return
    fi

    sha256sum "${file_path}" | awk '{print $1}'
}

platform_name() {
    local operating_system
    local architecture

    operating_system="$(uname -s)"
    architecture="$(uname -m)"

    case "${operating_system}:${architecture}" in
        Darwin:arm64)
            printf 'darwin_arm64\n'
            ;;
        Darwin:x86_64)
            printf 'darwin_amd64\n'
            ;;
        Linux:aarch64 | Linux:arm64)
            printf 'linux_arm64\n'
            ;;
        Linux:x86_64)
            printf 'linux_amd64\n'
            ;;
        *)
            printf 'Unsupported platform: %s %s\n' \
                "${operating_system}" "${architecture}" >&2
            exit 1
            ;;
    esac
}

gitleaks_platform_name() {
    local operating_system
    local architecture

    operating_system="$(uname -s)"
    architecture="$(uname -m)"

    case "${operating_system}:${architecture}" in
        Darwin:arm64)
            printf 'darwin_arm64\n'
            ;;
        Darwin:x86_64)
            printf 'darwin_x64\n'
            ;;
        Linux:aarch64 | Linux:arm64)
            printf 'linux_arm64\n'
            ;;
        Linux:x86_64)
            printf 'linux_x64\n'
            ;;
        *)
            printf 'Unsupported platform: %s %s\n' \
                "${operating_system}" "${architecture}" >&2
            exit 1
            ;;
    esac
}

install_release_binary() {
    local binary_name="$1"
    local version="$2"
    local archive_name="$3"
    local checksum_name="$4"
    local release_url="$5"
    local destination_path="${binary_directory}/${binary_name}"
    local temporary_directory
    local expected_checksum
    local actual_checksum

    if [[ -x "${destination_path}" ]] \
        && "${destination_path}" --version 2>&1 | grep -Fq "${version}"; then
        return
    fi

    temporary_directory="$(mktemp -d)"
    trap 'rm -rf "${temporary_directory}"' RETURN
    curl --fail --location --silent --show-error \
        --output "${temporary_directory}/${archive_name}" \
        "${release_url}/${archive_name}"
    curl --fail --location --silent --show-error \
        --output "${temporary_directory}/${checksum_name}" \
        "${release_url}/${checksum_name}"
    expected_checksum="$(awk -v archive_name="${archive_name}" \
        '$2 == archive_name || $2 == "*" archive_name {print $1}' \
        "${temporary_directory}/${checksum_name}")"

    if [[ -z "${expected_checksum}" ]]; then
        printf 'No checksum found for %s\n' "${archive_name}" >&2
        exit 1
    fi

    actual_checksum="$(sha256_file "${temporary_directory}/${archive_name}")"
    if [[ "${actual_checksum}" != "${expected_checksum}" ]]; then
        printf 'Checksum verification failed for %s\n' "${archive_name}" >&2
        exit 1
    fi

    tar -xzf "${temporary_directory}/${archive_name}" -C "${temporary_directory}"
    install -m 0755 \
        "$(find "${temporary_directory}" -type f -name "${binary_name}" -print -quit)" \
        "${destination_path}"
    "${destination_path}" --version >/dev/null
}

mkdir -p "${binary_directory}"

install_release_binary \
    trufflehog \
    "${TRUFFLEHOG_VERSION}" \
    "trufflehog_${TRUFFLEHOG_VERSION}_$(platform_name).tar.gz" \
    "trufflehog_${TRUFFLEHOG_VERSION}_checksums.txt" \
    "https://github.com/trufflesecurity/trufflehog/releases/download/v${TRUFFLEHOG_VERSION}"
install_release_binary \
    gitleaks \
    "${GITLEAKS_VERSION}" \
    "gitleaks_${GITLEAKS_VERSION}_$(gitleaks_platform_name).tar.gz" \
    "gitleaks_${GITLEAKS_VERSION}_checksums.txt" \
    "https://github.com/gitleaks/gitleaks/releases/download/v${GITLEAKS_VERSION}"
