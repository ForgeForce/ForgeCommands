import { Command, CommandRegistry } from "./commandLib";
import { GitHub } from "@actions/github/lib/utils";
import { getMemberTeams, getTeamMembers, parseTeam } from "./utils";
import { context } from '@actions/github';

function isInTeam(teamName: string): (client: InstanceType<typeof GitHub>) => Promise<boolean> {
    return async function(client) {
        const teams = await getMemberTeams(client, context.actor);
        return teams.includes(teamName);
    }
}

export function registerCommands(registry: CommandRegistry) {
    registry.register('assign', new Command(
        true, isInTeam('triagers'),
        async (args, client) => {
            const issueNumber = context.issue.number;
            const issue = await client.rest.issues.get({
                ... context.repo,
                issue_number: issueNumber
            })
            const author = issue.data.user!!;

            const assignees = issue.data.assignees?.map((assignee) => assignee.login)
            if (assignees) {
                console.log(`Removing old assigness (${assignees.join(', ')}) from issue #${issueNumber}`)
                await client.rest.issues.removeAssignees({
                    ...context.repo,
                    issue_number: issueNumber,
                    assignees: assignees
                })
            }
            
            var toAssign = await getTeamMembers(client, context.repo.owner, parseTeam(args!!));
            toAssign = toAssign.filter((assignee) => assignee != author.login);
            if (toAssign.length > 10) {
                toAssign = toAssign.slice(0, 10)
            }

            await client.rest.issues.addAssignees({
                ...context.repo,
                issue_number: issueNumber,
                assignees: assignees
            })
        }
    ))
}