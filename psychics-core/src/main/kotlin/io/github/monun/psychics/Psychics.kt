/*
 * Copyright (c) 2020 monun
 *
 *  Licensed under the General Public License, Version 3.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/gpl-3.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.github.monun.psychics

import io.github.monun.psychics.plugin.PsychicsPlugin
import io.github.monun.tap.fake.FakeEntityServer
import org.bukkit.entity.Player
import java.util.logging.Logger

object Psychics {
    lateinit var plugin: PsychicsPlugin
        private set

    lateinit var logger: Logger
        private set

    lateinit var psychicManager: PsychicManager
        private set

    lateinit var fakeEntityServer: FakeEntityServer
        private set

    internal fun initialize(
        plugin: PsychicsPlugin,
        logger: Logger,
        psychicManager: PsychicManager,
        fakeEntityServer: FakeEntityServer
    ) {
        this.plugin = plugin
        this.logger = logger
        this.psychicManager = psychicManager
        this.fakeEntityServer = fakeEntityServer
    }
}

val Player.esper: Esper
    get() = requireNotNull(Psychics.psychicManager.getEsper(this)) { "Not found esper for $this" }