/*
 * Copyright 2014 the original author or authors.
 *
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
 */
package com.bmuschko.gradle.docker

import org.gradle.testkit.runner.BuildResult

class DockerExecContainerFunctionalTest extends AbstractFunctionalTest {

    def "Execute command within running container"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerPullImage
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerExecContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerLogsContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer

            task pullImage(type: DockerPullImage) {
                repository = '$TEST_IMAGE'
                tag = '$TEST_IMAGE_TAG'
            }

            task createContainer(type: DockerCreateContainer) {
                dependsOn pullImage
                targetImageId { pullImage.getImageId() }
                cmd = ['sleep','10']
            }

            task startContainer(type: DockerStartContainer) {
                dependsOn createContainer
                targetContainerId { createContainer.getContainerId() }
            }

            task execContainer(type: DockerExecContainer) {
                dependsOn startContainer
                targetContainerId { startContainer.getContainerId() }
                cmd = ['echo', 'Hello World']
            }
            
            task logContainer(type: DockerLogsContainer) {
                dependsOn execContainer
                targetContainerId { startContainer.getContainerId() }
                follow = true
                tailAll = true
            }
            
            task removeContainer(type: DockerRemoveContainer) {
                dependsOn logContainer
                removeVolumes = true
                force = true
                targetContainerId { startContainer.getContainerId() }
            }

            task workflow {
                dependsOn removeContainer
            }
        """

        expect:
        BuildResult result = build('workflow')
        result.output.contains("Hello World")
    }

    def "Execute command within stopped container"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerPullImage
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerExecContainer

            task pullImage(type: DockerPullImage) {
                repository = '$TEST_IMAGE'
                tag = '$TEST_IMAGE_TAG'
            }

            task createContainer(type: DockerCreateContainer) {
                dependsOn pullImage
                targetImageId { pullImage.getImageId() }
                cmd = ['sleep','10']
            }

            task execContainer(type: DockerExecContainer) {
                dependsOn createContainer
                targetContainerId { createContainer.getContainerId() }
                cmd = ['echo', 'Hello World']
            }

            task workflow {
                dependsOn execContainer
            }
        """

        when:
            build('workflow')

       	then:
            Exception ex = thrown()
            ex.message.contains('is not running')
    }

    def "Execute command as a non-root user within a running container"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerPullImage
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerExecContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerLogsContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer

            task pullImage(type: DockerPullImage) {
                repository = '$TEST_IMAGE'
                tag = '$TEST_IMAGE_TAG'
            }

            task createContainer(type: DockerCreateContainer) {
                dependsOn pullImage
                targetImageId { pullImage.getImageId() }
                cmd = ['sleep','10']
            }

            task startContainer(type: DockerStartContainer) {
                dependsOn createContainer
                targetContainerId { createContainer.getContainerId() }
            }

            task execContainer(type: DockerExecContainer) {
                dependsOn startContainer
                targetContainerId { startContainer.getContainerId() }
                cmd = ['sh', '-c', 'id -u && id -g']
                user = '10000:10001'
            }

            task logContainer(type: DockerLogsContainer) {
                dependsOn execContainer
                targetContainerId { startContainer.getContainerId() }
                follow = true
                tailAll = true
            }

            task removeContainer(type: DockerRemoveContainer) {
                dependsOn logContainer
                removeVolumes = true
                force = true
                targetContainerId { startContainer.getContainerId() }
            }

            task workflow {
                dependsOn removeContainer
            }
        """

        expect:
        BuildResult result = build('workflow')
        result.output.contains('10000\n10001')
    }
}
