/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2019 Bosch Software Innovations GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

package com.here.ort.analyzer.managers

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

import com.here.ort.analyzer.AbstractPackageManagerFactory
import com.here.ort.analyzer.DotNetSupport
import com.here.ort.analyzer.PackageManager
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.Identifier
import com.here.ort.model.Project
import com.here.ort.model.ProjectAnalyzerResult
import com.here.ort.model.VcsInfo
import com.here.ort.model.config.AnalyzerConfiguration
import com.here.ort.model.config.RepositoryConfiguration

import java.io.File

/**
 * The [NuGet](https://www.nuget.org/) package manager for .NET.
 */
class NuGet(
    name: String,
    analyzerRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, analyzerRoot, analyzerConfig, repoConfig) {
    companion object {
        fun mapPackageReferences(definitionFile: File): Map<String, String> {
            val map = mutableMapOf<String, String>()
            val mapper = XmlMapper().registerKotlinModule()
            val packagesConfig = mapper.readValue<PackagesConfig>(definitionFile)

            packagesConfig.packages.forEach {
                map[it.id] = it.version
            }

            return map
        }
    }

    class Factory : AbstractPackageManagerFactory<NuGet>("NuGet") {
        override val globsForDefinitionFiles = listOf("packages.config")

        override fun create(
            analyzerRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = NuGet(managerName, analyzerRoot, analyzerConfig, repoConfig)
    }

    // See https://docs.microsoft.com/en-us/nuget/reference/packages-config.
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class PackagesConfig(
        @JsonProperty(value = "package")
        @JacksonXmlElementWrapper(useWrapping = false)
        val packages: List<Package>
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Package(
        @JacksonXmlProperty(isAttribute = true)
        val id: String,
        @JacksonXmlProperty(isAttribute = true)
        val version: String
    )

    override fun resolveDependencies(definitionFile: File): ProjectAnalyzerResult? {
        val workingDir = definitionFile.parentFile
        val nuget = DotNetSupport(mapPackageReferences(definitionFile), workingDir)

        val project = Project(
            id = Identifier(
                type = "nuget",
                namespace = "",
                name = workingDir.name,
                version = ""
            ),
            definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
            declaredLicenses = sortedSetOf(),
            vcs = VcsInfo.EMPTY,
            vcsProcessed = processProjectVcs(workingDir),
            homepageUrl = "",
            scopes = sortedSetOf(nuget.scope)
        )

        return ProjectAnalyzerResult(
            project = project,
            packages = nuget.packages.map { it.toCuratedPackage() }.toSortedSet(),
            errors = nuget.errors
        )
    }
}
