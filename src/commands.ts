import { Command, CommandRegistry } from "./commandLib";
import { GitHub } from "@actions/github/lib/utils";
import { getMemberTeams, getTeamMembers, parseTeam } from "./utils";
import { context } from '@actions/github';
import { getInput } from '@actions/core'

function isInTeam(teamName: string): (client: InstanceType<typeof GitHub>) => Promise<boolean> {
    return async function(client) {
        const teams = await getMemberTeams(client, context.actor);
        return teams.includes(teamName);
    }
}
function triageTeam(): string {
    return getInput('triage-team') ?? 'triage'
}

export function registerCommands(registry: CommandRegistry) {
    registry.register('assign', new Command(
        true, isInTeam(triageTeam()),
        async (args, client) => {
            const issueNumber = context.issue.number;
            const issue = await client.rest.issues.get({
                ... context.repo,
                issue_number: issueNumber
            })
            const author = issue.data.user!!;

            const assignees = issue.data.assignees?.map((assignee) => assignee.login)
            if (assignees && assignees.length > 0) {
                console.log(`Removing old assignees (${assignees.join(', ')}) from issue #${issueNumber}`)
                await client.rest.issues.removeAssignees({
                    ...context.repo,
                    issue_number: issueNumber,
                    assignees: assignees
                })
            }
            
            console.log(`Querying members of team '${parseTeam(args!!)}', organization: ${context.repo.owner}`)
            var toAssign = await getTeamMembers(client, context.repo.owner, parseTeam(args!!));
            toAssign = toAssign.filter((assignee) => assignee != author.login);
            if (toAssign.length > 10) {
                toAssign = toAssign.slice(0, 10)
            }
            if (toAssign.length <= 0) {
                console.log('No members to assign')
                return true
            }
            console.log(`Assigning ${toAssign.join(', ')} to issue #${issueNumber}`)

            await client.rest.issues.addAssignees({
                ...context.repo,
                issue_number: issueNumber,
                assignees: toAssign
            })

            return true
        }
    ))
}