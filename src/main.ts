import {
    getBooleanInput,
    getInput,
    setFailed,
} from "@actions/core";

import { CommandRegistry } from "./commandLib"
import { registerCommands } from "./commands";

export async function run(): Promise<void> {
    try {
        const registry = new CommandRegistry(
            getInput("prefix", { required: true }).replace("<ws>", " "),
            getBooleanInput("allow-edits")
        );
        registerCommands(registry);
        await registry.process();
    } catch (error) {
        if (error instanceof Error) {
            setFailed(error.message);
        } else {
            throw error;
        }
    }
}

run();