"""
One-shot script to embed physio knowledge base into ChromaDB.
Run: python -m app.langchain.embedder

Place PDF or .txt files in ml-service/knowledge_base/ before running.
"""
import logging
import os

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


def embed_knowledge_base() -> None:
    from langchain_community.document_loaders import DirectoryLoader, TextLoader
    from langchain.text_splitter import RecursiveCharacterTextSplitter
    from langchain_community.vectorstores import Chroma
    from langchain_community.embeddings import HuggingFaceEmbeddings

    kb_path = os.path.join(os.path.dirname(__file__), "../../knowledge_base")
    kb_path = os.path.abspath(kb_path)
    logger.info("Loading documents from: %s", kb_path)

    loader = DirectoryLoader(kb_path, glob="**/*.txt", loader_cls=TextLoader)
    documents = loader.load()

    if not documents:
        logger.warning("No .txt files found in knowledge_base/. Add documents and re-run.")
        return

    logger.info("Loaded %d documents. Splitting...", len(documents))

    splitter = RecursiveCharacterTextSplitter(chunk_size=500, chunk_overlap=50)
    chunks = splitter.split_documents(documents)
    logger.info("Created %d chunks.", len(chunks))

    embeddings = HuggingFaceEmbeddings(
        model_name="sentence-transformers/all-MiniLM-L6-v2"
    )

    chroma_host = os.getenv("CHROMA_HOST", "localhost")
    chroma_port = int(os.getenv("CHROMA_PORT", "8001"))

    vectordb = Chroma(
        collection_name="physio_knowledge",
        embedding_function=embeddings,
        client_settings={
            "chroma_server_host": chroma_host,
            "chroma_server_http_port": chroma_port,
        },
    )
    vectordb.add_documents(chunks)
    logger.info("Embedded %d chunks into ChromaDB @ %s:%s ✓", len(chunks), chroma_host, chroma_port)


if __name__ == "__main__":
    embed_knowledge_base()
