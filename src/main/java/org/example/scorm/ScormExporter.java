package org.example.scorm;

import javafx.scene.control.TreeItem;
import org.example.model.CourseNode;
import org.example.model.CourseNode.ContentType;
import org.example.model.CourseNode.NodeType;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ScormExporter {

    private Path tempDir;
    private final String contentFolderName = "content";
    private final String resourceFolderName = "resources";
    private int itemCounter = 0;
    private int resourceCounter = 0;

    public void export(TreeItem<CourseNode> root, File destinationFile) throws IOException, TransformerException, ParserConfigurationException {
        // 1. Création d'un dossier de travail temporaire
        tempDir = Files.createTempDirectory("scorm_export_");
        Path contentDir = tempDir.resolve(contentFolderName);
        Path resourcesDir = tempDir.resolve(resourceFolderName);
        Files.createDirectories(contentDir);
        Files.createDirectories(resourcesDir);

        // 2. Générer les ressources du cours (HTML, copies de fichiers, etc.)
        generateCourseContent(root, contentDir, resourcesDir);

        // 3. Générer le fichier imsmanifest.xml
        Document manifestDoc = createManifest(root);
        writeManifestToFile(manifestDoc);

        // 4. Compresser le tout dans un fichier ZIP
        zipDirectory(tempDir.toFile(), destinationFile);

        // 5. Nettoyer le dossier temporaire
        deleteDirectory(tempDir);
    }

    private void generateCourseContent(TreeItem<CourseNode> treeItem, Path contentDir, Path resourcesDir) throws IOException {
        CourseNode node = treeItem.getValue();

        if (node.getType() == NodeType.NOTION) {
            String contentFilePath = null;
            ContentType contentType = node.getContentType();

            switch (contentType) {
                case TEXT:
                    contentFilePath = "notion_" + (itemCounter++) + ".html";
                    String htmlContent = generateHtmlPage(node.getTitle(), node.getContent());
                    Files.write(contentDir.resolve(contentFilePath), htmlContent.getBytes());
                    break;
                case LINK:
                    contentFilePath = "link_" + (itemCounter++) + ".html";
                    String linkContent = generateLinkHtmlPage(node.getTitle(), node.getContent());
                    Files.write(contentDir.resolve(contentFilePath), linkContent.getBytes());
                    break;
                case PDF:
                case VIDEO:
                case AUDIO:
                    if (node.getContent() != null && !node.getContent().isEmpty()) {
                        File originalFile = new File(node.getContent());
                        String newFileName = "resource_" + (resourceCounter++) + getFileExtension(originalFile.getName());
                        Path newFilePath = resourcesDir.resolve(newFileName);
                        Files.copy(originalFile.toPath(), newFilePath);

                        contentFilePath = "media_" + (itemCounter++) + ".html";
                        String mediaContent = generateMediaHtmlPage(node.getTitle(), "../" + resourceFolderName + "/" + newFileName, contentType);
                        Files.write(contentDir.resolve(contentFilePath), mediaContent.getBytes());
                    }
                    break;
            }
            if (contentFilePath != null) {
                node.setContentFilePath(contentFolderName + "/" + contentFilePath);
            }
        }

        for (TreeItem<CourseNode> child : treeItem.getChildren()) {
            generateCourseContent(child, contentDir, resourcesDir);
        }
    }

    //-----------------------------------------------------------------------------------------------------------------
    // Méthodes de génération des pages HTML
    //-----------------------------------------------------------------------------------------------------------------

    private String generateHtmlPage(String title, String htmlBody) {
        return "<html><head><title>" + title + "</title></head><body><h1>" + title + "</h1>" + htmlBody + "</body></html>";
    }

    private String generateLinkHtmlPage(String title, String url) {
        return "<html><head><title>" + title + "</title><meta http-equiv=\"refresh\" content=\"0; url=" + url + "\"></head><body><h1>Redirection vers " + title + "</h1></body></html>";
    }

    private String generateMediaHtmlPage(String title, String relativePath, ContentType type) {
        String mediaTag;
        switch (type) {
            case PDF:
                mediaTag = "<iframe src=\"" + relativePath + "\" width=\"100%\" height=\"600\"></iframe>";
                break;
            case VIDEO:
                mediaTag = "<video controls src=\"" + relativePath + "\" style=\"width:100%;\"></video>";
                break;
            case AUDIO:
                mediaTag = "<audio controls src=\"" + relativePath + "\"></audio>";
                break;
            default:
                mediaTag = "<span>Type de média non supporté.</span>";
        }
        return "<html><head><title>" + title + "</title></head><body><h1>" + title + "</h1>" + mediaTag + "</body></html>";
    }

    //-----------------------------------------------------------------------------------------------------------------
    // Méthodes de génération du manifeste XML
    //-----------------------------------------------------------------------------------------------------------------

    private Document createManifest(TreeItem<CourseNode> root) throws ParserConfigurationException {
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        Document doc = docBuilder.newDocument();
        doc.setXmlStandalone(true);

        Element manifest = doc.createElement("manifest");
        manifest.setAttribute("identifier", "manifest_" + UUID.randomUUID().toString());
        manifest.setAttribute("version", "1.2");
        manifest.setAttribute("xmlns", "http://www.imsglobal.org/xsd/imscp_v1p1");
        manifest.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
        manifest.setAttribute("xmlns:adlcp", "http://www.adlnet.org/xsd/adlcp_rootv1p2");
        manifest.setAttribute("xsi:schemaLocation", "http://www.imsglobal.org/xsd/imscp_v1p1 imscp_v1p1.xsd http://www.adlnet.org/xsd/adlcp_rootv1p2 adlcp_rootv1p2.xsd");
        doc.appendChild(manifest);

        Element organizations = doc.createElement("organizations");
        manifest.appendChild(organizations);

        Element organization = doc.createElement("organization");
        organization.setAttribute("identifier", "org_" + UUID.randomUUID().toString());
        organization.setAttribute("structure", "hierarchical");
        organizations.appendChild(organization);

        Element title = doc.createElement("title");
        title.setTextContent(root.getValue().getTitle());
        organization.appendChild(title);

        Element resources = doc.createElement("resources");
        manifest.appendChild(resources);

        // Remplir l'organisation et les ressources de manière récursive
        fillManifest(doc, organization, resources, root);

        return doc;
    }

    private void fillManifest(Document doc, Element parentElement, Element resourcesElement, TreeItem<CourseNode> treeItem) {
        CourseNode node = treeItem.getValue();

        if (node.getType() == NodeType.COURS) {
            // Le noeud racine ne crée pas d'item
            for (TreeItem<CourseNode> child : treeItem.getChildren()) {
                fillManifest(doc, parentElement, resourcesElement, child);
            }
            return;
        }

        Element item = doc.createElement("item");
        item.setAttribute("identifier", "item_" + UUID.randomUUID().toString());
        item.setAttribute("identifierref", "res_" + node.getTitle().replaceAll("\\s+", "_"));
        item.setAttribute("isvisible", "true");
        parentElement.appendChild(item);

        Element itemTitle = doc.createElement("title");
        itemTitle.setTextContent(node.getTitle());
        item.appendChild(itemTitle);

        if (node.getType() == NodeType.NOTION && node.getContentFilePath() != null) {
            Element resource = doc.createElement("resource");
            resource.setAttribute("identifier", "res_" + node.getTitle().replaceAll("\\s+", "_"));
            resource.setAttribute("type", "webcontent");
            resource.setAttribute("href", node.getContentFilePath());
            resource.setAttribute("adlcp:scormtype", "sco");

            Element file = doc.createElement("file");
            file.setAttribute("href", node.getContentFilePath());
            resource.appendChild(file);

            resourcesElement.appendChild(resource);
        }

        for (TreeItem<CourseNode> child : treeItem.getChildren()) {
            fillManifest(doc, item, resourcesElement, child);
        }
    }

    private void writeManifestToFile(Document doc) throws TransformerException, IOException {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

        DOMSource source = new DOMSource(doc);
        File manifestFile = tempDir.resolve("imsmanifest.xml").toFile();
        StreamResult result = new StreamResult(manifestFile);

        transformer.transform(source, result);
    }

    //-----------------------------------------------------------------------------------------------------------------
    // Méthodes utilitaires de compression et de nettoyage
    //-----------------------------------------------------------------------------------------------------------------

    private void zipDirectory(File sourceDir, File destinationZipFile) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(destinationZipFile.toPath()))) {
            zipDirRecursive(sourceDir, sourceDir, zos);
        }
    }

    private void zipDirRecursive(File rootDir, File sourceDir, ZipOutputStream zos) throws IOException {
        for (File file : sourceDir.listFiles()) {
            if (file.isDirectory()) {
                zipDirRecursive(rootDir, file, zos);
            } else {
                String entryName = rootDir.toPath().relativize(file.toPath()).toString();
                zos.putNextEntry(new ZipEntry(entryName));
                Files.copy(file.toPath(), zos);
                zos.closeEntry();
            }
        }
    }

    private void deleteDirectory(Path path) throws IOException {
        Files.walk(path)
                .sorted((p1, p2) -> -p1.compareTo(p2)) // Pour effacer les enfants avant les parents
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        System.err.println("Échec de la suppression de " + p + ": " + e.getMessage());
                    }
                });
    }

    private String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? "" : fileName.substring(dotIndex);
    }
}