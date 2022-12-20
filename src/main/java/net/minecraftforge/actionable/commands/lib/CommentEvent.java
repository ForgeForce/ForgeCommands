package net.minecraftforge.actionable.commands.lib;

public record CommentEvent(
        String author_association, String body, String created_at,
        String html_url, int id, String issue_url, String node_id,
        String updated_at, String url
) {
}
