import * as core from '@actions/core';
import { context } from '@actions/github';
import { GitHub } from "@actions/github/lib/utils";

const memberTeamsQuery = `query($pg: String, $organization: String!, $userLogins: [String!], $username: String!)  {
    user(login: $username) {
        id
    }
    organization(login: $organization) {
      teams (first:1, userLogins: $userLogins, after: $pg) { 
          nodes {
            name
        }
        pageInfo {
          hasNextPage
          endCursor
        }        
      }
    }
}`

export async function getMemberTeams(octokit: InstanceType<typeof GitHub>, username: string): Promise<Array<string>> {
    const org = context.repo.owner
    var teams = []

    // Pagination
    var pg = null;
    let data: any = {};
    do {
        data = await octokit.graphql(memberTeamsQuery, {
            "pg": pg,
            "organization": org,
            "userLogins": [username],
            "username": username
        });

        teams = teams.concat(data.organization.teams.nodes.map((val: any) => val.name));

        pg = data.organization.teams.pageInfo.endCursor;
    } while (data.organization.teams.pageInfo.hasNextPage)
    return teams;
}

export async function getTeamMembers(octokit: InstanceType<typeof GitHub>, org: string, teamName: string): Promise<Array<string>> {
    const teamMemberRequest = await 
        octokit.rest.teams.listMembersInOrg({
                org,
                team_slug: teamName
    }).catch((err) => {
        const newErr = new Error('Failed to retrieve team members');
        newErr.stack += `\nCaused by: ${err.stack}`;
        throw newErr;
    });
    return teamMemberRequest.data
        .map((user) => user.login);
};

export function parseTeam(input: string) {
    return input.substring(input.indexOf("/") + 1, input.length)
}