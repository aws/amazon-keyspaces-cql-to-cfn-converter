/*-
 * #%L
 * Amazon Keyspaces CQL Script to CFN Template Converter
 * %%
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package software.aws.mcs.cql2cfn

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import software.aws.mcs.cql2cfn.cfn.CfnTemplateGenerator
import software.aws.mcs.cql2cfn.cql.CqlScriptParser
import software.aws.mcs.cql2cfn.util.Logger
import java.io.File

class Main : CliktCommand() {
    private val cqlScript: String by argument(
        "CQL_SCRIPT",
        help = "Path to a CQL script to be converted"
    ).file(canBeDir = false, mustBeReadable = true).convert { it.readText() }
    private val cfnTemplateFile: File? by argument(
        "CFN_TEMPLATE",
        help = "Path to the generated CloudFormation template"
    ).file(canBeDir = false).optional()
    private val debug: Boolean by option(
        "--debug", "-d",
        help = "Enable debug logging"
    ).flag()
    private val strict: Boolean by option(
        "--strict", "-s",
        help = "Exit on warnings in addition to errors"
    ).flag()

    override fun run() {
        Logger.debugEnabled = debug
        Logger.exitOnWarn = strict
        try {
            val cfnTemplate = convert(cqlScript)
            cfnTemplateFile?.writeText(cfnTemplate) ?: println(cfnTemplate)
        } catch (e: Exception) {
            Logger.error("Encountered an unexpected error", e)
        }
    }

    internal fun convert(cqlScript: String): String =
        CfnTemplateGenerator().apply {
            CqlScriptParser.parse(cqlScript).forEach(::handle)
        }.generate()
}

fun main(args: Array<String>): Unit =
    Main().main(args)
