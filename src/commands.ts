import { Command, CommandRegistry } from "./commandLib";
import { GitHub } from "@actions/github/lib/utils";
import { getMemberTeams, getTeamMembers, parseTeam } from "./utils";
import { context } from '@actions/github';
import { getInput } from '@actions/core'

type PermCheck = (client: InstanceType<typeof GitHub>) => Promise<boolean>

function isInTeam(teamName: string): PermCheck {
    return async function(client) {
        const teams = await getMemberTeams(client, context.actor);
        return teams.includes(teamName);
    }
}

type permission = 'none' | 'read' | 'write' | 'admin'
const permissions = ['none', 'read', 'write', 'admin']
function hasPermission(permission: permission): PermCheck {
    return async function(client) {
        const payload = await client.rest.repos.getCollaboratorPermissionLevel({
            ...context.repo,
            username: context.actor
        })
        if (!payload || !payload.data || !payload.data.permission) {
            console.log(`No user permission found in payload: ${JSON.stringify(payload, null, 2)}`);
            return false;
        }
        return permissions.indexOf(permission) <= permissions.indexOf(payload.data.permission)
    }
}

function triageTeam(): string {
    return getInput('triage-team') ?? 'triage'
}

function postComment(client: InstanceType<typeof GitHub>, comment: string): Promise<any> {
    return client.rest.issues.createComment({
        ...context.repo,
        issue_number: context.issue.number,
        body: comment
    })
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

    registry.register('shipit', new Command(
        false, hasPermission('write'),
        async (args, client) => {
            if (!((context as any).pull_request)) {
                await postComment(client, 'This command is only usable on pull requests!')
                return false
            }

            const { data: pullRequest } = await client.rest.pulls.get({
                ...context.repo,
                pull_number: context.issue.number
            })

            await client.rest.pulls.merge({
                ...context.repo,
                pull_number: context.issue.number,
                commit_title: pullRequest.title,
                commit_message: '',
                sha: pullRequest.head.sha,
                merge_method: 'squash'
            })
            await postComment(client, ':shipit:')

            return true
        }
    ))
}