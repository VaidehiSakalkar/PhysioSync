"""
RAG chain — ChromaDB + HuggingFace embeddings + Gemini Pro LLM.
"""
import logging
import os

logger = logging.getLogger(__name__)

_qa_chain = None


def init_vectorstore() -> None:
    """Called once at app startup to connect to ChromaDB."""
    global _qa_chain
    try:
        import chromadb
        from langchain_community.vectorstores import Chroma
        from langchain_community.embeddings import HuggingFaceEmbeddings
        from langchain.chains import RetrievalQA
        from langchain_google_genai import ChatGoogleGenerativeAI

        chroma_host = os.getenv("CHROMA_HOST", "localhost")
        chroma_port = int(os.getenv("CHROMA_PORT", "8001"))

        embeddings = HuggingFaceEmbeddings(
            model_name="sentence-transformers/all-MiniLM-L6-v2"
        )
        chroma_client = chromadb.HttpClient(host=chroma_host, port=chroma_port)
        vectordb = Chroma(
            collection_name="physio_knowledge",
            embedding_function=embeddings,
            client=chroma_client,
        )
        llm = ChatGoogleGenerativeAI(
            model="gemini-pro",
            google_api_key=os.getenv("GEMINI_API_KEY", ""),
        )
        _qa_chain = RetrievalQA.from_chain_type(
            llm=llm,
            retriever=vectordb.as_retriever(search_kwargs={"k": 5}),
            chain_type="stuff",
        )
        logger.info("RAG chain initialised (ChromaDB @ %s:%s).", chroma_host, chroma_port)
    except Exception as exc:
        logger.warning("RAG chain failed to initialise: %s. /api/ask will return stub.", exc)


def answer_patient_question(question: str, patient_context: dict) -> str:
    """Answer a patient question using RAG over the physio knowledge base."""
    if _qa_chain is None:
        return "AI assistant is not available at this time. Please contact your physiotherapist."

    enriched = (
        f"Patient context: {patient_context}\n"
        f"Patient question: {question}\n"
        f"Answer based on evidence-based physiotherapy guidelines."
    )
    return _qa_chain.run(enriched)
