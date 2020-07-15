import static org.hamcrest.Matchers.containsString
import static org.hamcrest.Matchers.hasItem
import static org.hamcrest.Matchers.instanceOf
import static org.hamcrest.Matchers.not
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertThat
import static org.junit.Assert.assertTrue
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static TerraformEnvironmentStage.PLAN;
import static org.mockito.Mockito.when;

import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.runner.RunWith
import de.bechte.junit.runners.context.HierarchicalContextRunner

@RunWith(HierarchicalContextRunner.class)
class TerraformPlanResultsPRPluginTest {

    @Before
    void resetJenkinsEnv() {
        Jenkinsfile.instance = mock(Jenkinsfile.class)
        when(Jenkinsfile.instance.getEnv()).thenReturn([:])
    }

    private configureJenkins(Map config = [:]) {
        Jenkinsfile.instance = mock(Jenkinsfile.class)
        when(Jenkinsfile.instance.getStandardizedRepoSlug()).thenReturn(config.repoSlug)
        when(Jenkinsfile.instance.getEnv()).thenReturn(config.env ?: [:])
    }

    public class Init {
        @After
        void resetPlugins() {
            TerraformPlanCommand.resetPlugins()
            TerraformEnvironmentStage.resetPlugins()
        }

        @Test
        void modifiesTerraformPlanCommand() {
            TerraformPlanResultsPRPlugin.init()

            Collection actualPlugins = TerraformPlanCommand.getPlugins()
            assertThat(actualPlugins, hasItem(instanceOf(TerraformPlanResultsPRPlugin.class)))
        }

        @Test
        void modifiesTerraformEnvironmentStageCommand() {
            TerraformPlanResultsPRPlugin.init()

            Collection actualPlugins = TerraformEnvironmentStage.getPlugins()
            assertThat(actualPlugins, hasItem(instanceOf(TerraformPlanResultsPRPlugin.class)))
        }
    }

    public class Apply {

        @Test
        void addsTeeArgumentToTerraformPlan() {
            TerraformPlanResultsPRPlugin plugin = new TerraformPlanResultsPRPlugin()
            TerraformPlanCommand command = new TerraformPlanCommand()

            plugin.apply(command)

            String result = command.toString()
            assertThat(result, containsString("-out=tfplan"))
            assertThat(result, containsString("2>plan.err | tee plan.out"))
        }

        @Test
        void decoratesTheTerraformEnvironmentStage()  {
            TerraformPlanResultsPRPlugin plugin = new TerraformPlanResultsPRPlugin()
            def environment = spy(new TerraformEnvironmentStage())
            configureJenkins(env: [
                'BRANCH_NAME': 'master',
                'BUILD_URL': 'https://my-jenkins/job/my-org/job/my-repo/job/PR-1/2/'
            ])

            plugin.apply(environment)

            verify(environment, times(1)).decorate(eq(TerraformEnvironmentStage.PLAN), any(Closure.class))
        }

    }

    class GetRepoSlug {
        @After
        void resetPlugin() {
            TerraformPlanResultsPRPlugin.reset()
            Jenkinsfile.reset()
        }

        @Test
        void returnsTheProvidedRepoSlug() {
            String expectedSlug = 'some/slug'
            TerraformPlanResultsPRPlugin.withRepoSlug(expectedSlug)
            def plugin = new TerraformPlanResultsPRPlugin()

            String actualSlug = plugin.getRepoSlug()

            assertEquals(expectedSlug, actualSlug)
        }

        @Test
        void defaultsToCurrentRepoSlug() {
            def expectedOrg = 'someOrg'
            def expectedRepo = 'someRepo'
            def jenkinsfileInstance = mock(Jenkinsfile.class)
            doReturn([organization: expectedOrg, repo: expectedRepo]).when(jenkinsfileInstance).getParsedScmUrl()
            Jenkinsfile.withInstance(jenkinsfileInstance)
            def plugin = new TerraformPlanResultsPRPlugin()

            String actualSlug = plugin.getRepoSlug()

            assertEquals("${expectedOrg}/${expectedRepo}".toString(), actualSlug.toString())
        }

    }

    class GetRepoHost {
        @Before
        void resetBefore() {
            TerraformPlanResultsPRPlugin.reset()
            Jenkinsfile.reset()
        }

        @After
        void reset() {
            TerraformPlanResultsPRPlugin.reset()
            Jenkinsfile.reset()
        }

        @Test
        void returnsTheProvidedHost() {
            String expectedHost = 'somehost'
            TerraformPlanResultsPRPlugin.withRepoHost(expectedHost)
            def plugin = new TerraformPlanResultsPRPlugin()

            String actualHost = plugin.getRepoHost()

            assertEquals(expectedHost, actualHost)
        }

        @Test
        void defaultsToTheHostOfTheProject() {
            def plugin = new TerraformPlanResultsPRPlugin()
            def jenkinsfileInstance = mock(Jenkinsfile.class)
            doReturn([protocol: 'https', domain: 'my.github.com']).when(jenkinsfileInstance).getParsedScmUrl()
            Jenkinsfile.withInstance(jenkinsfileInstance)

            String actualHost = plugin.getRepoHost()

            assertEquals('https://my.github.com', actualHost)
        }

        @Test
        void defaultsToTheProtocolOfTheProject() {
            def plugin = new TerraformPlanResultsPRPlugin()
            def jenkinsfileInstance = mock(Jenkinsfile.class)
            doReturn([protocol: 'http', domain: 'my.github.com']).when(jenkinsfileInstance).getParsedScmUrl()
            Jenkinsfile.withInstance(jenkinsfileInstance)

            String actualHost = plugin.getRepoHost()

            assertEquals('http://my.github.com', actualHost)
        }
    }

    class IsPullRequest {
        @Test
        void returnsTrueWhenBranchNameStartsWithPR() {
            def plugin = spy(new TerraformPlanResultsPRPlugin())
            doReturn('PR-thisIsAPullRequest').when(plugin).getBranchName()

            assertTrue(plugin.isPullRequest())
        }

        @Test
        void returnsfalseWhenBranchNameDoesNotStartWithPR() {
            def plugin = spy(new TerraformPlanResultsPRPlugin())
            doReturn('ThisIsNotA-PR').when(plugin).getBranchName()

            assertFalse(plugin.isPullRequest())
        }

    }

    class GetPullRequestNumber {
        @Test
        void parsesTheNumberFromTheBranchName() {
            def expectedNumber = "123"
            def plugin = spy(new TerraformPlanResultsPRPlugin())
            doReturn("PR-${expectedNumber}".toString()).when(plugin).getBranchName()

            def actualNumber = plugin.getPullRequestNumber()

            assertEquals(expectedNumber, actualNumber)
        }
    }

    class GetPullRequestCommentUrl {
        @Test
        void constructsTheUrlFromHostRepoSlugAndPrNumber() {
            def repoHost = 'someHost'
            def repoSlug = 'someSlug'
            def pullRequestNumber = 'somePr'
            def plugin = spy(new TerraformPlanResultsPRPlugin())
            doReturn(repoHost).when(plugin).getRepoHost()
            doReturn(repoSlug).when(plugin).getRepoSlug()
            doReturn(pullRequestNumber).when(plugin).getPullRequestNumber()

            def commentUrl = plugin.getPullRequestCommentUrl()

            assertEquals("${repoHost}/api/v3/repos/${repoSlug}/issues/${pullRequestNumber}/comments".toString(), commentUrl)
        }
    }

    class GetPlanOutput {
        @Test
        void readsContentsFromPlanOutFile() {
            def planOutFileContent = 'blahblahblah'
            def plugin = spy(new TerraformPlanResultsPRPlugin())
            doReturn(planOutFileContent).when(plugin).readFile('plan.out')
            doReturn(null).when(plugin).readFile('plan.err')

            def actualOutput = plugin.getPlanOutput()

            assertThat(actualOutput, containsString(planOutFileContent))
        }

        @Test
        void stripsWhitespaceEncodingFromContentsFromPlanOutFile() {
            def expectedOutput = 'blahblah'
            def plugin = spy(new TerraformPlanResultsPRPlugin())
            doReturn("   ${expectedOutput}   ".toString()).when(plugin).readFile('plan.out')
            doReturn(null).when(plugin).readFile('plan.err')

            def actualOutput = plugin.getPlanOutput()

            assertEquals(expectedOutput, actualOutput)
        }

        @Test
        void stripsAnsiColorEncodingFromContentsFromPlanOutFile() {
            def colorEncoding = '\u001b[32m'
            def plugin = spy(new TerraformPlanResultsPRPlugin())
            doReturn("${colorEncoding}blablah${colorEncoding}".toString()).when(plugin).readFile('plan.out')
            doReturn(null).when(plugin).readFile('plan.err')

            def planOutput = plugin.getPlanOutput()

            assertThat(planOutput, not(containsString(colorEncoding)))
        }

        class WithErrorOutput {
            @Test
            void includesContentsFromPlanErrorFileIfPresent() {
                def planErrorFileContent = 'errorcontent'
                def plugin = spy(new TerraformPlanResultsPRPlugin())
                doReturn('blahblah').when(plugin).readFile('plan.out')
                doReturn(planErrorFileContent).when(plugin).readFile('plan.err')

                def actualOutput = plugin.getPlanOutput()

                assertThat(actualOutput, containsString(planErrorFileContent))
            }

            @Test
            void stripsWhitespaceFromContentsFromPlanErrorFile() {
                def planOutput = 'planOut'
                def planError = 'planError'
                def plugin = spy(new TerraformPlanResultsPRPlugin())
                doReturn(planOutput).when(plugin).readFile('plan.out')
                doReturn("   ${planError}   ".toString()).when(plugin).readFile('plan.err')

                def actualOutput = plugin.getPlanOutput()

                assertEquals("${planOutput}\nSTDERR:\n${planError}".toString(), actualOutput)
            }

            @Test
            void stripsAnsiColorEncodingFromContentsFromPlanErrorFile() {
                def colorEncoding = '\u001b[32m'
                def plugin = spy(new TerraformPlanResultsPRPlugin())
                doReturn('planOut').when(plugin).readFile('plan.out')
                doReturn("${colorEncoding}blablah${colorEncoding}".toString()).when(plugin).readFile('plan.err')

                def actualOutput = plugin.getPlanOutput()

                assertThat(actualOutput, not(containsString(colorEncoding)))
            }

            @Test
            void ignoresPlanErrorFileContentIfEmpty() {
                def planOutput = 'planOut'
                def planError = ''
                def plugin = spy(new TerraformPlanResultsPRPlugin())
                doReturn(planOutput).when(plugin).readFile('plan.out')
                doReturn(planError).when(plugin).readFile('plan.err')

                def actualOutput = plugin.getPlanOutput()

                assertEquals(planOutput, actualOutput)
            }
        }
    }

    class GetCommentBody {
        @Test
        void usesThePlanOutput()  {
            def planOutput = 'someOutput'
            def plugin = spy(new TerraformPlanResultsPRPlugin())
            doReturn(planOutput).when(plugin).getPlanOutput()
            doReturn('someResult').when(plugin).getBuildResult()
            doReturn('someUrl').when(plugin).getBuildUrl()

            def actualComment = plugin.getCommentBody('myenv')

            assertThat(actualComment, containsString(planOutput))
        }

        @Test
        void usesTheCurrentBuildResult()  {
            def expectedBuildResult = 'expectedResult'
            def plugin = spy(new TerraformPlanResultsPRPlugin())
            doReturn('someOutput').when(plugin).getPlanOutput()
            doReturn(expectedBuildResult).when(plugin).getBuildResult()
            doReturn('someUrl').when(plugin).getBuildUrl()

            def actualComment = plugin.getCommentBody('myenv')

            assertThat(actualComment, containsString(expectedBuildResult))
        }

        @Test
        void usesTheCurrentBuildUrl()  {
            def expectedBuildUrl = 'expectedBuildUrl'
            def plugin = spy(new TerraformPlanResultsPRPlugin())
            doReturn('someOutput').when(plugin).getPlanOutput()
            doReturn('someResult').when(plugin).getBuildResult()
            doReturn(expectedBuildUrl).when(plugin).getBuildUrl()

            def actualComment = plugin.getCommentBody('myenv')

            assertThat(actualComment, containsString(expectedBuildUrl))
        }

        @Test
        void usesTheGivenEnvironment()  {
            def expectedEnvironment = 'expectedEnv'
            def plugin = spy(new TerraformPlanResultsPRPlugin())
            doReturn('someOutput').when(plugin).getPlanOutput()
            doReturn('someResult').when(plugin).getBuildResult()
            doReturn('someUrl').when(plugin).getBuildUrl()

            def actualComment = plugin.getCommentBody(expectedEnvironment)

            assertThat(actualComment, containsString(expectedEnvironment))
        }
    }
}
