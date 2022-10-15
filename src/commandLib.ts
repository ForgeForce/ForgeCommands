import { GitHub } from "@actions/github/lib/utils";
import {
    context,
    getOctokit,
} from "@actions/github";
import {
    debug,
    setFailed,
    getInput,
} from "@actions/core";

export class Command {
    constructor(public readonly hasArgument: boolean,
        public readonly check: (client: InstanceType<typeof GitHub>) => Promise<boolean>,
        public readonly callback: (args: string | null, client: InstanceType<typeof GitHub>) => Promise<boolean>) {
    }
}
interface CommentEvent {
    author_association:string;
    body: string;
    created_at:string;
    html_url:string;
    id: number;
    issue_url:string;
    node_id:string;
    updated_a?:string;
    url:string;
}

export class CommandRegistry {
    constructor(private readonly prefix: string, private readonly allowEdits: boolean, private commands:  Map<string, Command> = new Map<string, Command>()) {

    }

    public register(name: string, command: Command): void {
        this.commands.set(name, command);
    }

    public async process(): Promise<boolean> {
        debug("Checking if step should run on this event action");

        if (!this.shouldRunForAction()) {
            return false;
        }

        debug("Getting the comment and checking it for a command");

        const comment = context.payload.comment as CommentEvent;
        const parsedCommand = this.findCommand(comment.body);
        
        if (!parsedCommand) {
            setFailed("Comment did not contain a command");
            return false;
        }

        const command = this.commands.get(parsedCommand.name);
        if (!command) {
            setFailed("Command does not exist");
            return false;
        }
        const client = getOctokit(getInput("github-token", { required: true }));

        if (!(await command.check(client))) {
            setFailed("User does not have permission!");
            return false;
        }

        if (command.hasArgument && !parsedCommand.arguments) {
            setFailed("This command requires arguments!");
            await client.rest.issues.createComment({
                ...context.repo,
                issue_number: context.issue.number,
                body: "Please provide the arguments to run the command with!"
            });
            return false;
        }
        await command.callback(parsedCommand.arguments, client);

        debug("Command passed, able to execute job");
        return true;
    }

    public findCommand(comment: string): {
        name: string,
        arguments: string | null
    } | undefined {
        console.log(`Checking for prefix '${this.prefix}'`);
        if (comment.startsWith(this.prefix)) {
            const actualComment = comment.substring(this.prefix.length)
            const split = actualComment.split(" ")
            return {
                name: split[0],
                arguments: split.length == 1 ? null : split.slice(1).join(" ").trim(),
            }
        }
    }

    public shouldRunForAction(): boolean {
        if (context.payload.action === "created") {
            debug("Comment was created");
    
            return true;
        }
    
        if (context.payload.action === "edited") {
            if (this.allowEdits) {
                debug("Comment was edited and allow edits is enabled");
    
                return true;
            }
    
            setFailed("Comment was edited and allow edits is disabled, no action to take");
    
            return false;
        }
    
        debug(`Comment action is assumed to be deleted, no action to take, actual value '${context.payload.action}'`);
    
        return false;
    }
}