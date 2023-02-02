/*
 * Copyright (C) 2010, Christian Halstrick <christian.halstrick@sap.com>
 * Copyright (C) 2010, Stefan Lay <stefan.lay@sap.com>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.minecraftforge.actionable.util.git;

import org.eclipse.jgit.api.GitCommand;
import org.eclipse.jgit.api.errors.FilterFailedException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuildIterator;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.NameConflictTreeWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.WorkingTreeIterator;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.LinkedList;
import java.util.function.Predicate;

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.eclipse.jgit.lib.FileMode.GITLINK;
import static org.eclipse.jgit.lib.FileMode.TYPE_GITLINK;
import static org.eclipse.jgit.lib.FileMode.TYPE_TREE;

public class ExtensibleAddCommand extends GitCommand<DirCache> {

    private final Collection<TreeFilter> filters = new LinkedList<>();

    private boolean update = false;

    public ExtensibleAddCommand(Repository repo) {
        super(repo);
    }

    /**
     * Add a path to a file/directory whose content should be added.
     * <p>
     * A directory name (e.g. <code>dir</code> to add <code>dir/file1</code> and
     * <code>dir/file2</code>) can also be given to add all files in the
     * directory, recursively. Fileglobs (e.g. *.c) are not yet supported.
     *
     * @param filepattern repository-relative path of file/directory to add (with
     *                    <code>/</code> as separator)
     * @return {@code this}
     */
    public ExtensibleAddCommand addPattern(String filepattern) {
        return addFilter(PathFilter.create(filepattern));
    }

    public ExtensibleAddCommand addPatterns(Collection<? extends TreeFilter> filters) {
        checkCallable();
        this.filters.addAll(filters);
        return this;
    }

    public ExtensibleAddCommand all() {
        return addFilter(TreeFilter.ALL);
    }

    public ExtensibleAddCommand addFilter(Predicate<TreeWalk> filter, boolean recursive) {
        return addFilter(new TreeFilter() {
            @Override
            public boolean include(TreeWalk walker) {
                return filter.test(walker);
            }

            @Override
            public boolean shouldBeRecursive() {
                return recursive;
            }

            @Override
            public TreeFilter clone() {
                return this;
            }
        });
    }

    public ExtensibleAddCommand addFilter(TreeFilter filter) {
        checkCallable();
        filters.add(filter);
        return this;
    }

    /**
     * Executes the {@code Add} command. Each instance of this class should only
     * be used for one invocation of the command. Don't call this method twice
     * on an instance.
     *
     * @return the DirCache after Add
     */
    public DirCache call() throws GitAPIException {
        if (filters.isEmpty())
            throw new NoFilepatternException(JGitText.get().atLeastOnePatternIsRequired);
        checkCallable();
        DirCache dc = null;

        try (final ObjectInserter inserter = repo.newObjectInserter();
             final NameConflictTreeWalk tw = new NameConflictTreeWalk(repo)) {
            tw.setOperationType(TreeWalk.OperationType.CHECKIN_OP);
            dc = repo.lockDirCache();

            final DirCacheBuilder builder = dc.builder();
            tw.addTree(new DirCacheBuildIterator(builder));
            final FileTreeIterator itr = new FileTreeIterator(repo);
            itr.setDirCacheIterator(tw, 0);
            tw.addTree(itr);
            if (!filters.contains(PathFilter.ALL)) tw.setFilter(AndTreeFilter.create(filters));

            byte[] lastAdded = null;

            while (tw.next()) {
                final DirCacheIterator c = tw.getTree(0, DirCacheIterator.class);
                final WorkingTreeIterator f = tw.getTree(1, WorkingTreeIterator.class);
                if (c == null) {
                    if (f != null && f.isEntryIgnored()) {
                        // file is not in index but is ignored, do nothing
                        continue;
                    } else if (update) {
                        // Only update of existing entries was requested.
                        continue;
                    }
                }

                DirCacheEntry entry = c != null ? c.getDirCacheEntry() : null;
                if (entry != null && entry.getStage() > 0
                        && lastAdded != null
                        && lastAdded.length == tw.getPathLength()
                        && tw.isPathPrefix(lastAdded, lastAdded.length) == 0) {
                    // In case of an existing merge conflict the
                    // DirCacheBuildIterator iterates over all stages of
                    // this path, we however want to add only one
                    // new DirCacheEntry per path.
                    continue;
                }

                if (tw.isSubtree() && !tw.isDirectoryFileConflict()) {
                    tw.enterSubtree();
                    continue;
                }

                if (f == null) { // working tree file does not exist
                    if (entry != null && (!update || GITLINK == entry.getFileMode())) {
                        builder.add(entry);
                    }
                    continue;
                }

                if (entry != null && entry.isAssumeValid()) {
                    // Index entry is marked assume valid. Even though
                    // the user specified the file to be added JGit does
                    // not consider the file for addition.
                    builder.add(entry);
                    continue;
                }

                if ((f.getEntryRawMode() == TYPE_TREE
                        && f.getIndexFileMode(c) != FileMode.GITLINK) ||
                        (f.getEntryRawMode() == TYPE_GITLINK
                                && f.getIndexFileMode(c) == FileMode.TREE)) {
                    // Index entry exists and is symlink, gitlink or file,
                    // otherwise the tree would have been entered above.
                    // Replace the index entry by diving into tree of files.
                    tw.enterSubtree();
                    continue;
                }

                final byte[] path = tw.getRawPath();
                if (entry == null || entry.getStage() > 0) {
                    entry = new DirCacheEntry(path);
                }
                final FileMode mode = f.getIndexFileMode(c);
                entry.setFileMode(mode);

                if (GITLINK != mode) {
                    entry.setLength(f.getEntryLength());
                    entry.setLastModified(f.getEntryLastModified());
                    // We read and filter the content multiple times.
                    // f.getEntryContentLength() reads and filters the input and
                    // inserter.insert(...) does it again. That's because an
                    // ObjectInserter needs to know the length before it starts
                    // inserting. TODO: Fix this by using Buffers.
                    try (final InputStream in = f.openEntryStream()) {
                        final ObjectId id = inserter.insert(OBJ_BLOB, f.getEntryLength(), in);
                        entry.setObjectId(id);
                    }
                } else {
                    entry.setLength(0);
                    entry.setLastModified(0);
                    entry.setObjectId(f.getEntryObjectId());
                }
                builder.add(entry);
                lastAdded = path;
            }
            inserter.flush();
            builder.commit();
            setCallable(false);
        } catch (IOException e) {
            Throwable cause = e.getCause();
            if (cause instanceof FilterFailedException)
                throw (FilterFailedException) cause;
            throw new JGitInternalException(
                    JGitText.get().exceptionCaughtDuringExecutionOfAddCommand, e);
        } finally {
            if (dc != null)
                dc.unlock();
        }

        return dc;
    }

    public ExtensibleAddCommand setUpdate(boolean update) {
        this.update = update;
        return this;
    }
}

