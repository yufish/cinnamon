/*
 * Copyright (c) 2012 Ingo Wiarda
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE
 */
package cinnamon.index

import cinnamon.Folder
import cinnamon.ObjectSystemData
import groovy.util.logging.Log4j
import groovyx.gpars.actor.DynamicDispatchActor
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.index.IndexReader
import org.apache.lucene.index.Term
import org.apache.lucene.queryParser.QueryParser
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.Query
import org.apache.lucene.search.TermQuery
import org.apache.lucene.search.TopDocs
import org.apache.lucene.util.Version

import org.apache.lucene.xmlparser.CoreParser
import org.apache.lucene.xmlparser.ParserException
import cinnamon.index.queryBuilder.WildcardQueryBuilder
import cinnamon.index.queryBuilder.RegexQueryBuilder
import cinnamon.exceptions.CinnamonException

import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.LimitTokenCountAnalyzer

/**
 * Actor class which does the heavy lifting in searching and indexing.
 */
@Log4j
class LuceneActor extends DynamicDispatchActor {

//    Logger log = LoggerFactory.getLogger(this.class)

    void onDeliveryError(msg) {
        log.warn("Could not deliver msg: $msg")
    }

    void onMessage(IndexCommand command) {
        LuceneResult result = new LuceneResult()
        try {
            log.debug("onMessage: receveived indexCommand ${command.dump()}")
            switch (command.type) {
                case CommandType.REMOVE_FROM_INDEX: removeFromIndex(command); break
                case CommandType.UPDATE_INDEX: result = updateIndex(command); break
                case CommandType.ADD_TO_INDEX_NOW: command.removeFirst = false; doIndexJobNow(command); break
                case CommandType.REMOVE_FROM_INDEX_NOW: removeFromIndex(command); break
                case CommandType.UPDATE_INDEX_NOW: doIndexJobNow(command); break
                case CommandType.SEARCH: result = search(command); break;
                default: throw new RuntimeException("LuceneActor called with unknown command type: ${command.type}")
            }
            log.debug("reply & finish")
            reply result
        }
        catch (Exception e) {
            log.warn("Failed to act on command: ${command?.dump()}", e)
            result.failed = true
            result.errorMessage = e.message
            reply result
        }
    }

    LuceneResult updateIndex(IndexCommand command) {
        def repository = command.repository
        log.debug("Update repository: ${repository.name}")
        def osdJobs = []
        def seen = new HashSet<Long>(100)
        IndexJob.withTransaction {
            osdJobs = IndexJob.findAll("from IndexJob i where i.indexableClass=:indexableClass and i.failed = false",
                    [indexableClass: ObjectSystemData.class], [max: 100])
        }
        log.debug("Found ${osdJobs.size()} objects watiting for indexing in ${repository.name}.")
        osdJobs.each { IndexJob job ->
            ObjectSystemData.withTransaction {
                Long id = job.indexableId
                if (seen.contains(id)) {
                    // remove duplicate jobs in the current transaction
                    job.delete()
                    return
                }
                def osd = ObjectSystemData.get(id)
                doIndexJob(osd, job, repository, command.removeFirst)
                seen.add(id)
            }
        }
        def folderJobs = []
        def seenFolders = new HashSet<Long>(100)
        IndexJob.withTransaction {
            folderJobs = IndexJob.findAll("from IndexJob i where i.indexableClass=:indexableClass and i.failed=false",
                    [indexableClass: Folder.class], [max: 100])
        }
        log.debug("Found ${folderJobs.size()} folders waiting for indexing in ${repository.name}.")
        folderJobs.each { IndexJob job ->
            IndexJob.withTransaction {
                Long id = job.indexableId
                if (seenFolders.contains(id)) {
                    job.delete()
                    return
                }
                Folder reloadedFolder = Folder.get(id)
                doIndexJob(reloadedFolder, job, repository, command.removeFirst)
                seenFolders.add(id)
            }
        }
        def resultMessages = ["Updated osds: ${seen.size()}", "Updated folders. ${seenFolders.size()}"]
        def luceneResult = new LuceneResult(resultMessages: resultMessages)
        return luceneResult
    }

    def doIndexJobNow(IndexCommand command) {
        def repository = command.repository
        log.debug("Update repository: ${repository.name}")
        IndexJob.withNewTransaction {
            def reloadedIndexable = command.indexable.reload()
            doIndexJob(reloadedIndexable, null, repository, command.removeFirst)
        }
    }

    def doIndexJob(Indexable indexable, job, Repository repository, Boolean removeFirst) {
        if (indexable) {
            try {
                if (removeFirst) {
                    deleteIndexableFromIndex(indexable, repository)
                }
                addToIndex(indexable, repository)
            }
            catch (Exception e) {
                log.warn("Index job for ${indexable.toString()} failed with:", e)
                job?.failed = true
                return
            }
        }
        job?.delete()
    }

    void deleteIndexableFromIndex(Indexable indexable, Repository repository) {
        def uniqueId = indexable.uniqueId()
        log.debug("remove from Index: $uniqueId")
        deleteDocument(repository, new Term("uniqueId", uniqueId), 2);
    }

    LuceneResult search(IndexCommand command) {
        def repository = command.repository
        log.debug("search command for repository: " + repository)
        Query query

        if (command.xmlQuery) {
            Analyzer standardAnalyzer = new StandardAnalyzer(Version.LUCENE_36);
            def analyzer = new LimitTokenCountAnalyzer(standardAnalyzer, Integer.MAX_VALUE);
            InputStream bais = new ByteArrayInputStream(command.query.getBytes("UTF-8"));
            CoreParser coreParser = new CoreParser("content", analyzer);
            coreParser.addQueryBuilder("WildcardQuery", new WildcardQueryBuilder());
            coreParser.addQueryBuilder("RegexQuery", new RegexQueryBuilder());
            query = coreParser.parse(bais);
        }
        else {
            QueryParser queryParser = new QueryParser(Version.LUCENE_36, "content", new StandardAnalyzer(Version.LUCENE_36))
            query = queryParser.parse(command.query);
        }

        IndexSearcher searcher = repository.indexSearcher
        ResultCollector collector = new ResultCollector(reader: repository.indexReader,
                searcher: repository.indexSearcher, domain: command.domain)
        searcher.search(query, collector)
        log.debug("Found: ${collector.documents.size()} documents.")

        def luceneResult = new LuceneResult(itemIdMap: collector.itemIdMap)
        if (command.fields.size() > 0) {
            luceneResult.idFieldMap = collector.getIdFieldMap(command.domain, command.fields)
        }

        return luceneResult
    }

    void removeFromIndex(IndexCommand command) {
        try {
            def repository = command.repository
            def indexDir = repository.indexDir
            if (!IndexReader.indexExists(indexDir)) {
                log.debug("Index does not exist.");
                // nothing to do.
                return;
            }
            def indexable = command.indexable
            if (indexable == null) {
                log.debug("indexable is NULL");
                return;
            }
            deleteIndexableFromIndex(indexable, repository)
        } catch (FileNotFoundException f) {
            log.warn("File not found - if the index does not yet exist, " +
                    "removeFromIndex is expected to fail", f);
        } catch (IOException e) {
            throw new RuntimeException("error.lucene.IO", e);
        }
    }

    /**
     * Delete the documents found by the given Term from the given repository's index.
     *
     * @param term the search term
     * @param retries how often to retry deleting the document. Probably useful only
     *                if other programs / servlets are also interacting with the index.
     *                (which should not happen with an actor based implementation)
     * @throws java.io.IOException if anything IO-related goes wrong.
     */
    void deleteDocument(Repository repository, Term term, Integer retries) throws IOException {
        def indexWriter = repository.indexWriter
        try {
            indexWriter.deleteDocuments(term);
        } catch (Exception e) {
            log.warn("delete document failed:", e);
            if (retries != null && retries > 0) {
                log.info("retry-delete document");
                deleteDocument(repository, term, --retries);
            }
            throw new RuntimeException('delete document from index failed', e)
        } catch (OutOfMemoryError e) {
            log.error("OOM-error during indexing:", e);
            throw new RuntimeException('delete document from index failed', e)
        } finally {
            indexWriter.close(true)
            repository.createWriter()
        }
    }

    void addToIndex(Indexable indexable, Repository repository) {
        log.debug("store standard fields")
        IndexSearcher indexSearcher = repository.indexSearcher
        try {
            // check that the document does not already exist - otherwise, remove it.
            String uniqueId = "${indexable.class.name}@${indexable.id}"
            Term t = new Term("uniqueId", uniqueId)
            Query query = new TermQuery(t)
            TopDocs docs = indexSearcher.search(query, 1)
            if (docs.totalHits > 0) {
                log.debug("delete old version")
//                def docId = docs.scoreDocs[0].doc
                deleteDocument(repository, t, 2)
            }

            // add document to index:
            Document doc = new Document()
            doc = storeStandardFields(indexable, doc)

            ContentContainer content;
            if (indexable.hasXmlContent()) {
                content = new ContentContainer(indexable, repository.name);
            }
            else {
                content = new ContentContainer(indexable, "<empty />".getBytes())
            }
            doIndex(indexable, content, repository, doc)
        } catch (OutOfMemoryError e) {
            log.error("indexing failed: ", e)
            throw new RuntimeException('Indexing failed', e)
        }
        finally {
            repository.indexWriter.close(true)
            repository.createWriter()
        }
    }

    void doIndex(Indexable indexable, content, repository, doc) {
        ContentContainer metadata = new ContentContainer(indexable, indexable.getMetadata().getBytes());
        log.debug("store systemMetadata");
        String sysMeta = indexable.getSystemMetadata(true, true)
        ContentContainer systemMetadata = new ContentContainer(indexable, sysMeta.getBytes());
        log.debug("got sysMetadata, start indexObject loop");

        for (IndexItem item : IndexItem.list()) {
            log.debug("indexItem: $item.name")
            /*
            * At the moment, the OSDs and Folders do not cache
            * their responses to getSystemMetadata or getContent.
            * In a repository with many IndexItems, this would cause
            * quite some strain on the server's resources.
            */
            try {
//							log.debug("indexObject for field '"+item.fieldname+"' with content: "+content);
                log.debug("item.indexType: ${item.indexType}")
                item.indexObject(content, metadata, systemMetadata, doc);
            } catch (Exception e) {
                log.debug("*** failed *** to execute IndexItem " + item.name, e);
            }
        }
        repository.indexWriter.addDocument(doc)
    }

    Document storeStandardFields(Indexable indexable, Document doc) {
        String hibernateId = indexable.myId()
        String className = indexable.class.name
        String uniqueId = "${className}@${hibernateId}"
        log.debug("indexing of: ${uniqueId}")
        Field f = new Field("hibernateId", hibernateId, Field.Store.YES, Field.Index.NOT_ANALYZED)
        doc.add(f);

        doc.add(new Field("javaClass", className, Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field("uniqueId", uniqueId, Field.Store.YES, Field.Index.NOT_ANALYZED));
        return doc
    }

    /**
     * Search for all documents matching the given params, which must be an
     * Lucene XML-Query-Parser document.
     *
     * @param params input for XML-Query-Parser
     * @return a ResultCollector, which contains a collection of all documents found.
     */
    public ResultCollector search(String params, repository) {
        log.debug("starting search");
        ResultCollector results = new ResultCollector();
        def analyzer = new StandardAnalyzer(Version.LUCENE_36)
        def searcher = repository.indexSearcher
        try {
            InputStream bais = new ByteArrayInputStream(params.getBytes("UTF-8"));
            CoreParser coreParser = new CoreParser("content", analyzer);
            coreParser.addQueryBuilder("WildcardQuery", new WildcardQueryBuilder());
            coreParser.addQueryBuilder("RegexQuery", new RegexQueryBuilder());
            Query query = coreParser.parse(bais);

            results.setSearcher(searcher);
            searcher.search(query, results);
            searcher.close();
        } catch (IOException e) {
            throw new CinnamonException("error.lucene.IO", e);
        } catch (ParserException e) {
            throw new CinnamonException("error.parsing.lucene.query", e, params);
        } finally {

        }
        log.debug("finished search; results: " + results.getDocuments().size());
        return results;
    }

}
