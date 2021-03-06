/**
 * Copyright (C) 2010-2018 Structr GmbH
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.web.entity.dom;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.apache.commons.lang3.StringUtils;
import org.structr.api.Predicate;
import org.structr.api.util.Iterables;
import org.structr.common.CaseHelper;
import org.structr.common.ConstantBooleanTrue;
import org.structr.common.Filter;
import org.structr.common.Permission;
import org.structr.common.PropertyView;
import org.structr.common.SecurityContext;
import org.structr.common.error.ErrorBuffer;
import org.structr.common.error.FrameworkException;
import org.structr.common.error.SemanticErrorToken;
import org.structr.common.error.UnlicensedException;
import org.structr.core.GraphObject;
import org.structr.core.app.App;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.AbstractNode;
import org.structr.core.entity.LinkedTreeNode;
import org.structr.core.entity.Principal;
import org.structr.core.entity.Relation.Cardinality;
import org.structr.core.entity.Security;
import org.structr.core.graph.ModificationQueue;
import org.structr.core.graph.NodeInterface;
import org.structr.core.graph.RelationshipInterface;
import org.structr.core.property.BooleanProperty;
import org.structr.core.property.GenericProperty;
import org.structr.core.property.PropertyKey;
import org.structr.core.property.PropertyMap;
import org.structr.core.property.StringProperty;
import org.structr.core.script.Scripting;
import org.structr.schema.SchemaService;
import org.structr.schema.json.JsonObjectType;
import org.structr.schema.json.JsonReferenceType;
import org.structr.schema.json.JsonSchema;
import org.structr.web.common.AsyncBuffer;
import org.structr.web.common.GraphDataSource;
import org.structr.web.common.RenderContext;
import org.structr.web.common.RenderContext.EditMode;
import org.structr.web.common.StringRenderBuffer;
import org.structr.web.datasource.CypherGraphDataSource;
import org.structr.web.datasource.FunctionDataSource;
import org.structr.web.datasource.IdRequestParameterGraphDataSource;
import org.structr.web.datasource.RestDataSource;
import org.structr.web.datasource.XPathGraphDataSource;
import org.structr.web.entity.LinkSource;
import org.structr.web.entity.Linkable;
import org.structr.web.entity.Renderable;
import org.structr.web.property.CustomHtmlAttributeProperty;
import org.structr.websocket.command.CreateComponentCommand;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

/**
 * Combines AbstractNode and org.w3c.dom.Node.
 */
public interface DOMNode extends NodeInterface, Node, Renderable, DOMAdoptable, DOMImportable, LinkedTreeNode<DOMNode> {

	static class Impl { static {

		final JsonSchema schema   = SchemaService.getDynamicSchema();
		final JsonObjectType page = (JsonObjectType)schema.getType("Page");
		final JsonObjectType type = schema.addType("DOMNode");

		type.setIsAbstract();
		type.setImplements(URI.create("https://structr.org/v1.1/definitions/DOMNode"));
		type.setExtends(URI.create("https://structr.org/v1.1/definitions/LinkedTreeNodeImpl?typeParameters=org.structr.web.entity.dom.DOMNode"));

		type.addStringProperty("dataKey").setIndexed(true);
		type.addStringProperty("cypherQuery");
		type.addStringProperty("xpathQuery");
		type.addStringProperty("restQuery");
		type.addStringProperty("functionQuery");

		type.addStringProperty("showForLocales");
		type.addStringProperty("hideForLocales");
		type.addStringProperty("showConditions");
		type.addStringProperty("hideConditions");

		type.addStringProperty("sharedComponentConfiguration").setFormat("multi-line");

		type.addStringProperty("data-structr-id");
		type.addStringProperty("data-structr-hash");

		type.addBooleanProperty("renderDetails");
		type.addBooleanProperty("hideOnIndex");
		type.addBooleanProperty("hideOnDetail");
		type.addBooleanProperty("dontCache").setDefaultValue("false");
		type.addBooleanProperty("isDOMNode").setReadOnly(true).addTransformer(ConstantBooleanTrue.class.getName());

		type.addIntegerProperty("domSortPosition");

		type.addPropertyGetter("restQuery", String.class);
		type.addPropertyGetter("cypherQuery", String.class);
		type.addPropertyGetter("xpathQuery", String.class);
		type.addPropertyGetter("functionQuery", String.class);
		type.addPropertyGetter("dataKey", String.class);
		type.addPropertyGetter("showConditions", String.class);
		type.addPropertyGetter("hideConditions", String.class);

		type.addPropertyGetter("parent", DOMNode.class);
		type.addPropertyGetter("children", List.class);
		type.addPropertyGetter("nextSibling", DOMNode.class);
		type.addPropertyGetter("previousSibling", DOMNode.class);
		type.addPropertyGetter("syncedNodes", List.class);
		type.addPropertyGetter("ownerDocument", Page.class);
		type.addPropertyGetter("sharedComponent", DOMNode.class);
		type.addPropertyGetter("sharedComponentConfiguration", String.class);

		type.overrideMethod("onCreation",                  true,  DOMNode.class.getName() + ".onCreation(this, arg0, arg1);");
		type.overrideMethod("onModification",              true,  DOMNode.class.getName() + ".onModification(this, arg0, arg1, arg2);");

		type.overrideMethod("getPositionProperty",         false, "return DOMNodeCONTAINSDOMNode.positionProperty;");

		type.overrideMethod("getSiblingLinkType",          false, "return DOMNodeCONTAINS_NEXT_SIBLINGDOMNode.class;");
		type.overrideMethod("getChildLinkType",            false, "return DOMNodeCONTAINSDOMNode.class;");
		type.overrideMethod("getChildRelationships",       false, "return treeGetChildRelationships();");
		type.overrideMethod("renderNodeList",              false, DOMNode.class.getName() + ".renderNodeList(this, arg0, arg1, arg2, arg3);");

		type.overrideMethod("setVisibility",               false, "setProperty(visibleToPublicUsers, arg0); setProperty(visibleToAuthenticatedUsers, arg1);");

		type.overrideMethod("getClosestPage",              false, "return " + DOMNode.class.getName() + ".getClosestPage(this);");
		type.overrideMethod("getClosestTemplate",          false, "return " + DOMNode.class.getName() + ".getClosestTemplate(this, arg0);");
		type.overrideMethod("getContent",                  false, "return " + DOMNode.class.getName() + ".getContent(this, arg0);");
		type.overrideMethod("getOwnerDocumentAsSuperUser", false, "return " + DOMNode.class.getName() + ".getOwnerDocumentAsSuperUser(this);");
		type.overrideMethod("isSharedComponent",           false, "return " + DOMNode.class.getName() + ".isSharedComponent(this);");

		type.overrideMethod("getChildPosition",            false, "return treeGetChildPosition(arg0);");
		type.overrideMethod("getPositionPath",             false, "return " + DOMNode.class.getName() + ".getPositionPath(this);");

		type.overrideMethod("getIdHash",                   false, "return getUuid();");
		type.overrideMethod("getIdHashOrProperty",         false, "return " + DOMNode.class.getName() + ".getIdHashOrProperty(this);");
		type.overrideMethod("getDataHash",                 false, "return getProperty(datastructrhashProperty);");

		type.overrideMethod("avoidWhitespace",             false, "return false;");
		type.overrideMethod("isVoidElement",               false, "return false;");

		type.overrideMethod("inTrash",                     false, "return getParent() == null && getOwnerDocumentAsSuperUser() == null;");
		type.overrideMethod("dontCache",                   false, "return getProperty(dontCacheProperty);");
		type.overrideMethod("renderDetails",               false, "return getProperty(renderDetailsProperty);");
		type.overrideMethod("hideOnIndex",                 false, "return getProperty(hideOnIndexProperty);");
		type.overrideMethod("hideOnDetail",                false, "return getProperty(hideOnDetailProperty);");
		type.overrideMethod("isSynced",                    false, "return getSyncedNodes().size() > 0 || getSharedComponent() != null;");

		// ----- interface org.w3c.dom.Node -----
		type.overrideMethod("setUserData",                         false, "return null;");
		type.overrideMethod("getUserData",                         false, "return null;");
		type.overrideMethod("getFeature",                          false, "return null;");
		type.overrideMethod("isEqualNode",                         false, "return equals(arg0);");
		type.overrideMethod("lookupNamespaceURI",                  false, "return null;");
		type.overrideMethod("lookupPrefix",                        false, "return null;");
		type.overrideMethod("compareDocumentPosition",             false, "return 0;");
		type.overrideMethod("isDefaultNamespace",                  false, "return true;");
		type.overrideMethod("isSameNode",                          false, "return " + DOMNode.class.getName() + ".isSameNode(this, arg0);");
		type.overrideMethod("isSupported",                         false, "return false;");
		type.overrideMethod("getPrefix",                           false, "return null;");
		type.overrideMethod("setPrefix",                           false, "");
		type.overrideMethod("getNamespaceURI",                     false, "return null;");
		type.overrideMethod("getBaseURI",                          false, "return null;");
		type.overrideMethod("cloneNode",                           false, "return " + DOMNode.class.getName() + ".cloneNode(this, arg0);");
		type.overrideMethod("setTextContent",                      false, "");
		type.overrideMethod("getTextContent",                      false, "");

		// DOM operations
		type.overrideMethod("normalize",                           false, DOMNode.class.getName() + ".normalize(this);");
		type.overrideMethod("checkHierarchy",                      false, DOMNode.class.getName() + ".checkHierarchy(this, arg0);");
		type.overrideMethod("checkSameDocument",                   false, DOMNode.class.getName() + ".checkSameDocument(this, arg0);");
		type.overrideMethod("checkWriteAccess",                    false, DOMNode.class.getName() + ".checkWriteAccess(this);");
		type.overrideMethod("checkReadAccess",                     false, DOMNode.class.getName() + ".checkReadAccess(this);");
		type.overrideMethod("checkIsChild",                        false, DOMNode.class.getName() + ".checkIsChild(this, arg0);");
		type.overrideMethod("handleNewChild",                      false, DOMNode.class.getName() + ".handleNewChild(this, arg0);");
		type.overrideMethod("insertBefore",                        false, "return " + DOMNode.class.getName() + ".insertBefore(this, arg0, arg1);");
		type.overrideMethod("replaceChild",                        false, "return " + DOMNode.class.getName() + ".replaceChild(this, arg0, arg1);");
		type.overrideMethod("removeChild",                         false, "return " + DOMNode.class.getName() + ".removeChild(this, arg0);");
		type.overrideMethod("appendChild",                         false, "return " + DOMNode.class.getName() + ".appendChild(this, arg0);");
		type.overrideMethod("hasChildNodes",                       false, "return !getProperty(childrenProperty).isEmpty();");

		type.overrideMethod("displayForLocale",                    false, "return " + DOMNode.class.getName() + ".displayForLocale(this, arg0);");
		type.overrideMethod("displayForConditions",                false, "return " + DOMNode.class.getName() + ".displayForConditions(this, arg0);");

		// Renderable
		type.overrideMethod("render",                              false, DOMNode.class.getName() + ".render(this, arg0, arg1);");

		// DOMAdoptable
		type.overrideMethod("doAdopt",                             false, "return " + DOMNode.class.getName() + ".doAdopt(this, arg0);");

		// LinkedTreeNode
		type.overrideMethod("doAppendChild",                       false, "checkWriteAccess(); treeAppendChild(arg0);");
		type.overrideMethod("doRemoveChild",                       false, "checkWriteAccess(); treeRemoveChild(arg0);");
		type.overrideMethod("getFirstChild",                       false, "checkReadAccess(); return (DOMNode)treeGetFirstChild();");
		type.overrideMethod("getLastChild",                        false, "checkReadAccess(); return (DOMNode)treeGetLastChild();");
		type.overrideMethod("getChildNodes",                       false, "checkReadAccess(); return new " + DOMNodeList.class.getName() + "(treeGetChildren());");
		type.overrideMethod("getParentNode",                       false, "checkReadAccess(); return getParent();");

		type.overrideMethod("renderCustomAttributes",              false, DOMNode.class.getName() + ".renderCustomAttributes(this, arg0, arg1, arg2);");
		type.overrideMethod("getSecurityInstructions",             false, DOMNode.class.getName() + ".getSecurityInstructions(this, arg0);");
		type.overrideMethod("getVisibilityInstructions",           false, DOMNode.class.getName() + ".getVisibilityInstructions(this, arg0);");
		type.overrideMethod("getLinkableInstructions",             false, DOMNode.class.getName() + ".getLinkableInstructions(this, arg0);");
		type.overrideMethod("getContentInstructions",              false, DOMNode.class.getName() + ".getContentInstructions(this, arg0);");
		type.overrideMethod("renderSharedComponentConfiguration",  false, DOMNode.class.getName() + ".renderSharedComponentConfiguration(this, arg0, arg1);");
		type.overrideMethod("getPagePath",                         false, "return " + DOMNode.class.getName() + ".getPagePath(this);");
		type.overrideMethod("getDataPropertyKeys",                 false, "return " + DOMNode.class.getName() + ".getDataPropertyKeys(this);");
		type.overrideMethod("getAllChildNodes",                    false, "return " + DOMNode.class.getName() + ".getAllChildNodes(this);");

		type.addMethod("setOwnerDocument")
			.setSource("setProperty(ownerDocumentProperty, (Page)ownerDocument);")
			.addException(FrameworkException.class.getName())
			.addParameter("ownerDocument", "org.structr.web.entity.dom.Page");

		type.addMethod("setSharedComponent")
			.setSource("setProperty(sharedComponentProperty, (DOMNode)sharedComponent);")
			.addException(FrameworkException.class.getName())
			.addParameter("sharedComponent", "org.structr.web.entity.dom.DOMNode");

		final JsonReferenceType siblings = type.relate(type, "CONTAINS_NEXT_SIBLING", Cardinality.OneToOne,  "previousSibling", "nextSibling");
		final JsonReferenceType parent   = type.relate(type, "CONTAINS",              Cardinality.OneToMany, "parent",          "children");
		final JsonReferenceType synced   = type.relate(type, "SYNC",                  Cardinality.OneToMany, "sharedComponent", "syncedNodes");
		final JsonReferenceType owner    = type.relate(page, "PAGE",                  Cardinality.ManyToOne, "elements",        "ownerDocument");

		type.addIdReferenceProperty("parentId",      parent.getSourceProperty());
		type.addIdReferenceProperty("childrenIds",   parent.getTargetProperty());
		type.addIdReferenceProperty("pageId",        owner.getTargetProperty());
		type.addIdReferenceProperty("nextSiblingId", siblings.getTargetProperty());

		// sort position of children in page
		parent.addIntegerProperty("position");
	}}

	static final String PAGE_CATEGORY              = "Page Structure";
	static final String EDIT_MODE_BINDING_CATEGORY = "Edit Mode Binding";
	static final String QUERY_CATEGORY             = "Query and Data Binding";

	// ----- error messages for DOMExceptions -----
	public static final String NO_MODIFICATION_ALLOWED_MESSAGE         = "Permission denied.";
	public static final String INVALID_ACCESS_ERR_MESSAGE              = "Permission denied.";
	public static final String INDEX_SIZE_ERR_MESSAGE                  = "Index out of range.";
	public static final String CANNOT_SPLIT_TEXT_WITHOUT_PARENT        = "Cannot split text element without parent and/or owner document.";
	public static final String WRONG_DOCUMENT_ERR_MESSAGE              = "Node does not belong to this document.";
	public static final String HIERARCHY_REQUEST_ERR_MESSAGE_SAME_NODE = "A node cannot accept itself as a child.";
	public static final String HIERARCHY_REQUEST_ERR_MESSAGE_ANCESTOR  = "A node cannot accept its own ancestor as child.";
	public static final String HIERARCHY_REQUEST_ERR_MESSAGE_DOCUMENT  = "A document may only have one html element.";
	public static final String HIERARCHY_REQUEST_ERR_MESSAGE_ELEMENT   = "A document may only accept an html element as its document element.";
	public static final String NOT_SUPPORTED_ERR_MESSAGE               = "Node type not supported.";
	public static final String NOT_FOUND_ERR_MESSAGE                   = "Node is not a child.";
	public static final String NOT_SUPPORTED_ERR_MESSAGE_IMPORT_DOC    = "Document nodes cannot be imported into another document.";
	public static final String NOT_SUPPORTED_ERR_MESSAGE_ADOPT_DOC     = "Document nodes cannot be adopted by another document.";
	public static final String NOT_SUPPORTED_ERR_MESSAGE_RENAME        = "Renaming of nodes is not supported by this implementation.";

	public static final List<GraphDataSource<Iterable<GraphObject>>> listSources = new LinkedList<>(Arrays.asList(
		new IdRequestParameterGraphDataSource("nodeId"),
		new RestDataSource(),
		new FunctionDataSource(),
		new CypherGraphDataSource(),
		new XPathGraphDataSource()
	));

	public static final Set<String> cloneBlacklist = new LinkedHashSet<>(Arrays.asList(new String[] {
		"id", "type", "ownerDocument", "pageId", "parent", "parentId", "syncedNodes", "children", "childrenIds", "linkable", "linkableId", "path"
	}));

	public static final String[] rawProps = new String[] {
		"dataKey", "restQuery", "cypherQuery", "xpathQuery", "functionQuery", "hideOnIndex", "hideOnDetail", "showForLocales", "hideForLocales", "showConditions", "hideConditions"
	};

	boolean isSynced();
	boolean isSharedComponent();
	boolean contentEquals(final DOMNode otherNode);
	boolean isVoidElement();
	boolean avoidWhitespace();
	boolean inTrash();
	boolean dontCache();
	boolean hideOnIndex();
	boolean hideOnDetail();
	boolean renderDetails();
	boolean displayForLocale(final RenderContext renderContext);
	boolean displayForConditions(final RenderContext renderContext);

	int getChildPosition(final DOMNode otherNode);

	String getIdHash();
	String getIdHashOrProperty();
	String getShowConditions();
	String getHideConditions();
	String getContent(final RenderContext.EditMode editMode) throws FrameworkException;
	String getDataHash();
	String getDataKey();
	String getPositionPath();

	String getCypherQuery();
	String getRestQuery();
	String getXpathQuery();
	String getFunctionQuery();

	String getPagePath();
	String getContextName();
	String getSharedComponentConfiguration();

	@Override
	DOMNode getPreviousSibling();

	@Override
	DOMNode getNextSibling();

	DOMNode getParent();
	DOMNode getSharedComponent();
	List<DOMNode> getChildren();
	List<DOMNode> getSyncedNodes();

	@Override
	Page getOwnerDocument();
	Page getOwnerDocumentAsSuperUser();
	Page getClosestPage();

	void setOwnerDocument(final Page page) throws FrameworkException;
	void setSharedComponent(final DOMNode sharedComponent) throws FrameworkException;

	Template getClosestTemplate(final Page page);

	void updateFromNode(final DOMNode otherNode) throws FrameworkException;
	void setVisibility(final boolean publicUsers, final boolean authenticatedUsers) throws FrameworkException;
	void renderNodeList(final SecurityContext securityContext, final RenderContext renderContext, final int depth, final String dataKey) throws FrameworkException;
	void handleNewChild(final Node newChild);
	void checkIsChild(final Node otherNode) throws DOMException;
	void checkHierarchy(Node otherNode) throws DOMException;
	void checkSameDocument(Node otherNode) throws DOMException;
	void checkWriteAccess() throws DOMException;
	void checkReadAccess() throws DOMException;

	void renderCustomAttributes(final AsyncBuffer out, final SecurityContext securityContext, final RenderContext renderContext) throws FrameworkException;
	void getSecurityInstructions(final Set<String> instructions);
	void getVisibilityInstructions(final Set<String> instructions);
	void getLinkableInstructions(final Set<String> instructions);
	void getContentInstructions(final Set<String> instructions);
	void renderSharedComponentConfiguration(final AsyncBuffer out, final EditMode editMode);

	List<RelationshipInterface> getChildRelationships();

	void doAppendChild(final DOMNode node) throws FrameworkException;
	void doRemoveChild(final DOMNode node) throws FrameworkException;

	Set<PropertyKey> getDataPropertyKeys();


	// ----- static methods -----
	static void onCreation(final DOMNode thisNode, final SecurityContext securityContext, final ErrorBuffer errorBuffer) throws FrameworkException {

		DOMNode.checkName(thisNode, errorBuffer);
	}

	static void onModification(final DOMNode thisNode, final SecurityContext securityContext, final ErrorBuffer errorBuffer, final ModificationQueue modificationQueue) throws FrameworkException {

		DOMNode.increasePageVersion(thisNode);
		DOMNode.checkName(thisNode, errorBuffer);
	}

	public static String escapeForHtml(final String raw) {
		return StringUtils.replaceEach(raw, new String[]{"&", "<", ">"}, new String[]{"&amp;", "&lt;", "&gt;"});
	}

	public static String escapeForHtmlAttributes(final String raw) {
		return StringUtils.replaceEach(raw, new String[]{"&", "<", ">", "\""}, new String[]{"&amp;", "&lt;", "&gt;", "&quot;"});
	}

	public static String unescapeForHtmlAttributes(final String raw) {
		return StringUtils.replaceEach(raw, new String[]{"&amp;", "&lt;", "&gt;", "&quot;"}, new String[]{"&", "<", ">", "\""});
	}

	public static String objectToString(final Object source) {

		if (source != null) {
			return source.toString();
		}

		return null;
	}

	/**
	 * Recursively clone given node, all its direct children and connect the cloned child nodes to the clone parent node.
	 *
	 * @param securityContext
	 * @param nodeToClone
	 * @return
	 */
	public static DOMNode cloneAndAppendChildren(final SecurityContext securityContext, final DOMNode nodeToClone) {

		final DOMNode newNode               = (DOMNode)nodeToClone.cloneNode(false);
		final List<DOMNode> childrenToClone = (List<DOMNode>)nodeToClone.getChildNodes();

		for (final DOMNode childNodeToClone : childrenToClone) {

			newNode.appendChild((DOMNode)cloneAndAppendChildren(securityContext, childNodeToClone));
		}

		return newNode;
	}

	public static Set<DOMNode> getAllChildNodes(final DOMNode node) {

		Set<DOMNode> allChildNodes = new HashSet<>();

		getAllChildNodes(node, allChildNodes);

		return allChildNodes;
	}

	public static void getAllChildNodes(final DOMNode node, final Set<DOMNode> allChildNodes) {

		Node n = node.getFirstChild();

		while (n != null) {

			if (n instanceof DOMNode) {

				DOMNode domNode = (DOMNode)n;

				if (!allChildNodes.contains(domNode)) {

					allChildNodes.add(domNode);
					allChildNodes.addAll(getAllChildNodes(domNode));

				} else {

					// break loop!
					break;
				}
			}

			n = n.getNextSibling();
		}
	}

	public static void renderNodeList(final DOMNode node, final SecurityContext securityContext, final RenderContext renderContext, final int depth, final String dataKey) throws FrameworkException {

		final Iterable<GraphObject> listSource = renderContext.getListSource();
		if (listSource != null) {

			for (final GraphObject dataObject : listSource) {

				// make current data object available in renderContext
				renderContext.putDataObject(dataKey, dataObject);
				node.renderContent(renderContext, depth + 1);

			}

			renderContext.clearDataObject(dataKey);
		}
	}

	public static Template getClosestTemplate(final DOMNode thisNode, final Page page) {

		DOMNode node = thisNode;

		while (node != null) {

			if (node instanceof Template) {

				final Template template = (Template)node;

				Document doc = template.getOwnerDocument();

				if (doc == null) {

					doc = node.getClosestPage();
				}

				if (doc != null && (page == null || doc.equals(page))) {

					return template;

				}

				final List<DOMNode> _syncedNodes = template.getSyncedNodes();

				for (final DOMNode syncedNode : _syncedNodes) {

					doc = syncedNode.getOwnerDocument();

					if (doc != null && (page == null || doc.equals(page))) {

						return (Template)syncedNode;

					}
				}
			}

			node = (DOMNode)node.getParentNode();
		}

		return null;
	}

	public static Page getClosestPage(final DOMNode thisNode) {

		DOMNode node = thisNode;

		while (node != null) {

			if (node instanceof Page) {

				return (Page)node;
			}

			node = (DOMNode)node.getParentNode();

		}

		return null;
	}

	public static String getPositionPath(final DOMNode thisNode) {

		String path = "";

		DOMNode currentNode = thisNode;
		while (currentNode.getParentNode() != null) {

			DOMNode parentNode = (DOMNode)currentNode.getParentNode();

			path = "/" + parentNode.getChildPosition(currentNode) + path;

			currentNode = parentNode;

		}

		return path;

	}

	public static String getContent(final DOMNode thisNode, final RenderContext.EditMode editMode) throws FrameworkException {

		final RenderContext ctx         = new RenderContext(thisNode.getSecurityContext(), null, null, editMode);
		final StringRenderBuffer buffer = new StringRenderBuffer();

		ctx.setBuffer(buffer);
		thisNode.render(ctx, 0);

		// extract source
		return buffer.getBuffer().toString();
	}

	public static String getIdHashOrProperty(final DOMNode thisNode) {

		String idHash = thisNode.getDataHash();
		if (idHash == null) {

			idHash = thisNode.getIdHash();
		}

		return idHash;
	}

	public static Page getOwnerDocumentAsSuperUser(final DOMNode thisNode) {

		final RelationshipInterface ownership = thisNode.getOutgoingRelationshipAsSuperUser(StructrApp.getConfiguration().getRelationshipEntityClass("DOMNodePAGEPage"));
		if (ownership != null) {

			return (Page)ownership.getTargetNode();
		}

		return null;
	}

	public static boolean isSharedComponent(final DOMNode thisNode) {

		final Document _ownerDocument = thisNode.getOwnerDocumentAsSuperUser();
		if (_ownerDocument != null) {

			try {

				return _ownerDocument.equals(CreateComponentCommand.getOrCreateHiddenDocument());

			} catch (FrameworkException fex) {

				logger.warn("Unable fetch ShadowDocument node: {}", fex.getMessage());
			}
		}

		return false;
	}

	public static boolean isSameNode(final DOMNode thisNode, Node node) {

		if (node != null && node instanceof DOMNode) {

			String otherId = ((DOMNode)node).getProperty(GraphObject.id);
			String ourId   = thisNode.getProperty(GraphObject.id);

			if (ourId != null && otherId != null && ourId.equals(otherId)) {
				return true;
			}
		}

		return false;
	}

	public static Node cloneNode(final DOMNode thisNode, boolean deep) {

		final SecurityContext securityContext = thisNode.getSecurityContext();

		if (deep) {

			return cloneAndAppendChildren(securityContext, thisNode);

		} else {

			final PropertyMap properties = new PropertyMap();

			for (Iterator<PropertyKey> it = thisNode.getPropertyKeys(PropertyView.Ui).iterator(); it.hasNext();) {

				final PropertyKey key = it.next();

				// skip blacklisted properties
				if (cloneBlacklist.contains(key.jsonName())) {
					continue;
				}


				if (!key.isUnvalidated()) {
					properties.put(key, thisNode.getProperty(key));
				}
			}

			// htmlView is necessary for the cloning of DOM nodes - otherwise some properties won't be cloned
			for (Iterator<PropertyKey> it = thisNode.getPropertyKeys(PropertyView.Html).iterator(); it.hasNext();) {

				final PropertyKey key = it.next();

				// skip blacklisted properties
				if (cloneBlacklist.contains(key.jsonName())) {
					continue;
				}

				if (!key.isUnvalidated()) {
					properties.put(key, thisNode.getProperty(key));
				}
}


			if (thisNode instanceof LinkSource) {

				final LinkSource linkSourceElement = (LinkSource)thisNode;

				properties.put(StructrApp.key(LinkSource.class, "linkable"), linkSourceElement.getLinkable());

			}

			final App app = StructrApp.getInstance(securityContext);

			try {
				return app.create(thisNode.getClass(), properties);

			} catch (FrameworkException ex) {

				ex.printStackTrace();

				throw new DOMException(DOMException.INVALID_STATE_ERR, ex.toString());
			}
		}
	}

	static String getTextContent(final DOMNode thisNode) throws DOMException {

		final DOMNodeList results         = new DOMNodeList();
		final TextCollector textCollector = new TextCollector();

		DOMNode.collectNodesByPredicate(thisNode.getSecurityContext(), thisNode, results, textCollector, 0, false);

		return textCollector.getText();
	}

	static void collectNodesByPredicate(final SecurityContext securityContext, Node startNode, DOMNodeList results, Predicate<Node> predicate, int depth, boolean stopOnFirstHit) {

		if (predicate instanceof Filter) {

			((Filter)predicate).setSecurityContext(securityContext);
		}

		if (predicate.accept(startNode)) {

			results.add(startNode);

			if (stopOnFirstHit) {

				return;
			}
		}

		NodeList _children = startNode.getChildNodes();
		if (_children != null) {

			int len = _children.getLength();
			for (int i = 0; i < len; i++) {

				Node child = _children.item(i);

				collectNodesByPredicate(securityContext, child, results, predicate, depth + 1, stopOnFirstHit);
			}
		}
	}

	public static void normalize(final DOMNode thisNode) {

		final Document document = thisNode.getOwnerDocument();
		if (document != null) {

			// merge adjacent text nodes until there is only one left
			Node child = thisNode.getFirstChild();
			while (child != null) {

				if (child instanceof Text) {

					Node next = child.getNextSibling();
					if (next != null && next instanceof Text) {

						String text1 = child.getNodeValue();
						String text2 = next.getNodeValue();

						// create new text node
						final Text newText = document.createTextNode(text1.concat(text2));

						thisNode.removeChild(child);
						thisNode.insertBefore(newText, next);
						thisNode.removeChild(next);

						child = newText;

					} else {

						// advance to next node
						child = next;
					}

				} else {

					// advance to next node
					child = child.getNextSibling();

				}
			}

			// recursively normalize child nodes
			if (thisNode.hasChildNodes()) {

				Node currentChild = thisNode.getFirstChild();
				while (currentChild != null) {

					currentChild.normalize();
					currentChild = currentChild.getNextSibling();
				}
			}
		}
	}

	static Node appendChild(final DOMNode thisNode, final Node newChild) throws DOMException {

		thisNode.checkWriteAccess();
		thisNode.checkSameDocument(newChild);
		thisNode.checkHierarchy(newChild);

		try {

			if (newChild instanceof DocumentFragment) {

				// When inserting document fragments, we must take
				// care of the special case that the nodes already
				// have a NEXT_LIST_ENTRY relationship coming from
				// the document fragment, so we must first remove
				// the node from the document fragment and then
				// add it to the new parent.
				// replace indirectly using insertBefore and remove
				final DocumentFragment fragment = (DocumentFragment)newChild;
				Node currentChild = fragment.getFirstChild();

				while (currentChild != null) {

					// save next child in fragment list for later use
					final Node savedNextChild = currentChild.getNextSibling();

					// remove child from document fragment
					fragment.removeChild(currentChild);

					// append child to new parent
					thisNode.appendChild(currentChild);

					// next
					currentChild = savedNextChild;
				}

			} else {

				final Node _parent = newChild.getParentNode();

				if (_parent != null && _parent instanceof DOMNode) {
					_parent.removeChild(newChild);
				}

				thisNode.doAppendChild((DOMNode)newChild);

				// allow parent to set properties in new child
				thisNode.handleNewChild(newChild);
			}

		} catch (FrameworkException fex) {

			throw new DOMException(DOMException.INVALID_STATE_ERR, fex.toString());
		}

		return newChild;
	}

	public static Node removeChild(final DOMNode thisNode, final Node node) throws DOMException {

		thisNode.checkWriteAccess();
		thisNode.checkSameDocument(node);
		thisNode.checkIsChild(node);

		try {

			thisNode.doRemoveChild((DOMNode)node);

		} catch (FrameworkException fex) {

			throw new DOMException(DOMException.INVALID_STATE_ERR, fex.toString());
		}

		return node;
	}

	static void checkIsChild(final DOMNode thisNode, final Node otherNode) throws DOMException {

		if (otherNode instanceof DOMNode) {

			Node _parent = otherNode.getParentNode();

			if (!thisNode.isSameNode(_parent)) {

				throw new DOMException(DOMException.NOT_FOUND_ERR, NOT_FOUND_ERR_MESSAGE);
			}

			// validation successful
			return;
		}

		throw new DOMException(DOMException.NOT_SUPPORTED_ERR, NOT_SUPPORTED_ERR_MESSAGE);
	}

	static void checkHierarchy(final DOMNode thisNode, final Node otherNode) throws DOMException {

		// we can only check DOMNodes
		if (otherNode instanceof DOMNode) {

			// verify that the other node is not this node
			if (thisNode.isSameNode(otherNode)) {
				throw new DOMException(DOMException.HIERARCHY_REQUEST_ERR, HIERARCHY_REQUEST_ERR_MESSAGE_SAME_NODE);
			}

			// verify that otherNode is not one of the
			// the ancestors of this node
			// (prevent circular relationships)
			Node _parent = thisNode.getParentNode();
			while (_parent != null) {

				if (_parent.isSameNode(otherNode)) {
					throw new DOMException(DOMException.HIERARCHY_REQUEST_ERR, HIERARCHY_REQUEST_ERR_MESSAGE_ANCESTOR);
				}

				_parent = _parent.getParentNode();
			}

			// TODO: check hierarchy constraints imposed by the schema
			// validation successful
			return;
		}

		throw new DOMException(DOMException.NOT_SUPPORTED_ERR, NOT_SUPPORTED_ERR_MESSAGE);
	}

	static void checkSameDocument(final DOMNode thisNode, final Node otherNode) throws DOMException {

		Document doc = thisNode.getOwnerDocument();

		if (doc != null) {

			Document otherDoc = otherNode.getOwnerDocument();

			// Shadow doc is neutral
			if (otherDoc != null && !doc.equals(otherDoc) && !(doc instanceof ShadowDocument)) {

				logger.warn("{} node with UUID {} has owner document {} with UUID {} whereas this node has owner document {} with UUID {}",
					otherNode.getClass().getSimpleName(),
					((NodeInterface)otherNode).getUuid(),
					otherDoc.getClass().getSimpleName(),
					((NodeInterface)otherDoc).getUuid(),
					doc.getClass().getSimpleName(),
					((NodeInterface)doc).getUuid()
				);

				throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, WRONG_DOCUMENT_ERR_MESSAGE);
			}

			if (otherDoc == null) {

				((DOMNode)otherNode).doAdopt((Page)doc);

			}
		}
	}

	static void checkWriteAccess(final DOMNode thisNode) throws DOMException {

		if (!thisNode.isGranted(Permission.write, thisNode.getSecurityContext())) {

			throw new DOMException(DOMException.NO_MODIFICATION_ALLOWED_ERR, NO_MODIFICATION_ALLOWED_MESSAGE);
		}
	}

	static void checkReadAccess(final DOMNode thisNode) throws DOMException {

		final SecurityContext securityContext = thisNode.getSecurityContext();

		if (securityContext.isVisible(thisNode) || thisNode.isGranted(Permission.read, securityContext)) {
			return;
		}

		throw new DOMException(DOMException.INVALID_ACCESS_ERR, INVALID_ACCESS_ERR_MESSAGE);
	}

	static String indent(final int depth, final RenderContext renderContext) {

		if (!renderContext.shouldIndentHtml()) {
			return "";
		}

		StringBuilder indent = new StringBuilder("\n");

		for (int d = 0; d < depth; d++) {

			indent.append("	");

		}

		return indent.toString();
	}

	static boolean displayForLocale(final DOMNode thisNode, final RenderContext renderContext) {

		// In raw or widget mode, render everything
		EditMode editMode = renderContext.getEditMode(thisNode.getSecurityContext().getUser(false));
		if (EditMode.DEPLOYMENT.equals(editMode) || EditMode.RAW.equals(editMode) || EditMode.WIDGET.equals(editMode)) {
			return true;
		}

		String localeString = renderContext.getLocale().toString();

		String show = thisNode.getProperty(StructrApp.key(DOMNode.class, "showForLocales"));
		String hide = thisNode.getProperty(StructrApp.key(DOMNode.class, "hideForLocales"));

		// If both fields are empty, render node
		if (StringUtils.isBlank(hide) && StringUtils.isBlank(show)) {
			return true;
		}

		// If locale string is found in hide, don't render
		if (StringUtils.contains(hide, localeString)) {
			return false;
		}

		// If locale string is found in hide, don't render
		if (StringUtils.isNotBlank(show) && !StringUtils.contains(show, localeString)) {
			return false;
		}

		return true;

	}

	static boolean displayForConditions(final DOMNode thisNode, final RenderContext renderContext) {

		// In raw or widget mode, render everything
		EditMode editMode = renderContext.getEditMode(renderContext.getSecurityContext().getUser(false));
		if (EditMode.DEPLOYMENT.equals(editMode) || EditMode.RAW.equals(editMode) || EditMode.WIDGET.equals(editMode)) {
			return true;
		}

		String _showConditions = thisNode.getProperty(StructrApp.key(DOMNode.class, "showConditions"));
		String _hideConditions = thisNode.getProperty(StructrApp.key(DOMNode.class, "hideConditions"));

		// If both fields are empty, render node
		if (StringUtils.isBlank(_hideConditions) && StringUtils.isBlank(_showConditions)) {
			return true;
		}
		try {
			// If hide conditions evaluate to "true", don't render
			if (StringUtils.isNotBlank(_hideConditions) && Boolean.TRUE.equals(Scripting.evaluate(renderContext, thisNode, "${".concat(_hideConditions).concat("}"), "hide condition"))) {
				return false;
			}

		} catch (UnlicensedException|FrameworkException ex) {
			logger.error("Hide conditions " + _hideConditions + " could not be evaluated.", ex);
		}
		try {
			// If show conditions evaluate to "false", don't render
			if (StringUtils.isNotBlank(_showConditions) && Boolean.FALSE.equals(Scripting.evaluate(renderContext, thisNode, "${".concat(_showConditions).concat("}"), "show condition"))) {
				return false;
			}

		} catch (UnlicensedException|FrameworkException ex) {
			logger.error("Show conditions " + _showConditions + " could not be evaluated.", ex);
		}

		return true;

	}

	static boolean renderDeploymentExportComments(final DOMNode thisNode, final AsyncBuffer out, final boolean isContentNode) {

		final Set<String> instructions = new LinkedHashSet<>();

		thisNode.getVisibilityInstructions(instructions);
		thisNode.getLinkableInstructions(instructions);
		thisNode.getSecurityInstructions(instructions);

		if (isContentNode) {

			// special rules apply for content nodes: since we can not store
			// structr-specific properties in the attributes of the element,
			// we need to encode those attributes in instructions.
			thisNode.getContentInstructions(instructions);
		}

		if (!instructions.isEmpty()) {

			out.append("<!-- ");

			for (final Iterator<String> it = instructions.iterator(); it.hasNext();) {

				final String instruction = it.next();

				out.append(instruction);

				if (it.hasNext()) {
					out.append(", ");
				}
			}

			out.append(" -->");

			return true;

		} else {

			return false;
		}
	}

	static void renderSharedComponentConfiguration(final DOMNode thisNode, final AsyncBuffer out, final EditMode editMode) {

		if (EditMode.DEPLOYMENT.equals(editMode)) {

			final String configuration = thisNode.getProperty(StructrApp.key(DOMNode.class, "sharedComponentConfiguration"));
			if (StringUtils.isNotBlank(configuration)) {

				out.append(" data-structr-meta-shared-component-configuration=\"");
				out.append(escapeForHtmlAttributes(configuration));
				out.append("\"");
			}
		}
	}

	static void getContentInstructions(final DOMNode thisNode, final Set<String> instructions) {

		final String _contentType = thisNode.getProperty(StructrApp.key(Content.class, "contentType"));
		if (_contentType != null) {

			instructions.add("@structr:content(" + escapeForHtmlAttributes(_contentType) + ")");
		}

		final String _showConditions = thisNode.getShowConditions();
		if (StringUtils.isNotEmpty(_showConditions)) {

			instructions.add("@structr:show(" + escapeForHtmlAttributes(_showConditions) + ")");
		}

		final String _hideConditions = thisNode.getHideConditions();
		if (StringUtils.isNotEmpty(_hideConditions)) {

			instructions.add("@structr:hide(" + escapeForHtmlAttributes(_hideConditions) + ")");
		}
	}

	static void getLinkableInstructions(final DOMNode thisNode, final Set<String> instructions) {

		if (thisNode instanceof LinkSource) {

			final LinkSource linkSourceElement = (LinkSource)thisNode;
			final Linkable linkable            = linkSourceElement.getLinkable();

			if (linkable != null) {

				final String linkableInstruction = (linkable instanceof Page) ? "pagelink" : "link";
				final String path                = linkable.getPath();

				if (path != null) {

					instructions.add("@structr:" + linkableInstruction + "(" + path + ")");

				} else {

					logger.warn("Cannot export linkable relationship, no path.");
				}
			}
		}
	}

	static void getVisibilityInstructions(final DOMNode thisNode, final Set<String> instructions) {

		final Page _ownerDocument       = (Page)thisNode.getOwnerDocument();

		if(_ownerDocument == null) {

			logger.warn("DOMNode {} has no owner document!", thisNode.getUuid());
		}

		final boolean pagePublic        = _ownerDocument != null ? _ownerDocument.isVisibleToPublicUsers() : false;
		final boolean pageProtected     = _ownerDocument != null ? _ownerDocument.isVisibleToAuthenticatedUsers() : false;
		final boolean pagePrivate       = !pagePublic && !pageProtected;
		final boolean pagePublicOnly    = pagePublic && !pageProtected;
		final boolean elementPublic     = thisNode.isVisibleToPublicUsers();
		final boolean elementProtected  = thisNode.isVisibleToAuthenticatedUsers();
		final boolean elementPrivate    = !elementPublic && !elementProtected;
		final boolean elementPublicOnly = elementPublic && !elementProtected;

		if (pagePrivate && !elementPrivate) {

			if (elementPublicOnly) {
				instructions.add("@structr:public-only");
				return;
			}

			if (elementPublic && elementProtected) {
				instructions.add("@structr:public");
				return;
			}

			if (elementProtected) {
				instructions.add("@structr:protected");
				return;
			}
		}

		if (pageProtected && !elementProtected) {

			if (elementPublicOnly) {
				instructions.add("@structr:public-only");
				return;
			}

			if (elementPublic && elementProtected) {
				instructions.add("@structr:public");
				return;
			}

			if (elementPrivate) {
				instructions.add("@structr:private");
				return;
			}
		}

		if (pagePublic && !elementPublic) {

			if (elementPublicOnly) {
				instructions.add("@structr:public-only");
				return;
			}

			if (elementProtected) {
				instructions.add("@structr:protected");
				return;
			}

			if (elementPrivate) {
				instructions.add("@structr:private");
				return;
			}
		}

		if (pagePublicOnly && !elementPublicOnly) {

			if (elementPublic && elementProtected) {
				instructions.add("@structr:public");
				return;
			}

			if (elementProtected) {
				instructions.add("@structr:protected");
				return;
			}

			if (elementPrivate) {
				instructions.add("@structr:private");
				return;
			}

			return;
		}
	}

	static void getSecurityInstructions(final DOMNode thisNode, final Set<String> instructions) {

		final Principal _owner = thisNode.getOwnerNode();
		if (_owner != null) {

			instructions.add("@structr:owner(" + _owner.getProperty(AbstractNode.name) + ")");
		}

		for (final Security security : thisNode.getSecurityRelationships()) {

			if (security != null) {

				final Principal grantee = security.getSourceNode();
				final Set<String> perms = security.getPermissions();
				final StringBuilder shortPerms = new StringBuilder();

				// first character only
				for (final String perm : perms) {
					if (perm.length() > 0) {
						shortPerms.append(perm.substring(0, 1));
					}
				}

				if (shortPerms.length() > 0) {
					// ignore SECURITY-relationships without permissions
					instructions.add("@structr:grant(" + grantee.getProperty(AbstractNode.name) + "," + shortPerms.toString() + ")");
				}
			}
		}
	}

	static void renderCustomAttributes(final DOMNode thisNode, final AsyncBuffer out, final SecurityContext securityContext, final RenderContext renderContext) throws FrameworkException {

		final EditMode editMode = renderContext.getEditMode(securityContext.getUser(false));

		for (PropertyKey key : thisNode.getDataPropertyKeys()) {

			String value = "";

			if (EditMode.DEPLOYMENT.equals(editMode)) {

				final Object obj = thisNode.getProperty(key);
				if (obj != null) {

					value = obj.toString();
				}

			} else {

				value = thisNode.getPropertyWithVariableReplacement(renderContext, key);
				if (value != null) {

					value = value.trim();
				}
			}

			if (!(EditMode.RAW.equals(editMode) || EditMode.WIDGET.equals(editMode))) {

				value = escapeForHtmlAttributes(value);
			}

			if (StringUtils.isNotBlank(value)) {

				if (key instanceof CustomHtmlAttributeProperty) {
					out.append(" ").append(((CustomHtmlAttributeProperty)key).cleanName()).append("=\"").append(value).append("\"");
				} else {
					out.append(" ").append(key.dbName()).append("=\"").append(value).append("\"");
				}
			}
		}

		if (EditMode.DEPLOYMENT.equals(editMode) || EditMode.RAW.equals(editMode) || EditMode.WIDGET.equals(editMode)) {

			if (EditMode.DEPLOYMENT.equals(editMode)) {

				// export name property if set
				final String name = thisNode.getProperty(AbstractNode.name);
				if (name != null) {

					out.append(" data-structr-meta-name=\"").append(escapeForHtmlAttributes(name)).append("\"");
				}
			}

			for (final String p : rawProps) {

				String htmlName = "data-structr-meta-" + CaseHelper.toUnderscore(p, false).replaceAll("_", "-");
				Object value    = thisNode.getProperty(p);

				if (value != null) {

					final PropertyKey key    = StructrApp.key(DOMNode.class, p);
					final boolean isBoolean  = key instanceof BooleanProperty;
					final String stringValue = value.toString();

					if ((isBoolean && "true".equals(stringValue)) || (!isBoolean && StringUtils.isNotBlank(stringValue))) {
						out.append(" ").append(htmlName).append("=\"").append(escapeForHtmlAttributes(stringValue)).append("\"");
					}
				}
			}
		}
	}

	static Set<PropertyKey> getDataPropertyKeys(final DOMNode thisNode) {

		final Set<PropertyKey> customProperties = new TreeSet<>();
		final org.structr.api.graph.Node dbNode = thisNode.getNode();
		final Iterable<String> props            = dbNode.getPropertyKeys();

		for (final String key : props) {

			PropertyKey propertyKey = StructrApp.getConfiguration().getPropertyKeyForJSONName(thisNode.getClass(), key, false);
			if (propertyKey == null) {

				// support arbitrary data-* attributes
				propertyKey = new StringProperty(key);
			}

			if (key.startsWith("data-")) {

				if (propertyKey != null && propertyKey instanceof BooleanProperty && dbNode.hasProperty(key)) {

					final Object defaultValue = propertyKey.defaultValue();
					final Object nodeValue    = dbNode.getProperty(key);

					// don't export boolean false values (which is the default)
					if (nodeValue != null && Boolean.FALSE.equals(nodeValue) && (defaultValue == null || nodeValue.equals(defaultValue))) {

						continue;
					}
				}

				customProperties.add(propertyKey);

			} else if (key.startsWith(CustomHtmlAttributeProperty.CUSTOM_HTML_ATTRIBUTE_PREFIX)) {

				final CustomHtmlAttributeProperty customProp = new CustomHtmlAttributeProperty(propertyKey);

				customProperties.add(customProp);
			}
		}

		return customProperties;
	}

	static void handleNewChild(final DOMNode thisNode, Node newChild) {

		final Page page = (Page)thisNode.getOwnerDocument();

		for (final DOMNode child : DOMNode.getAllChildNodes(thisNode)) {

			try {

				child.setOwnerDocument(page);

			} catch (FrameworkException ex) {
				logger.warn("", ex);
			}
		}
	}

	static void render(final DOMNode thisNode, final RenderContext renderContext, final int depth) throws FrameworkException {

		final SecurityContext securityContext = renderContext.getSecurityContext();
		if (!securityContext.isVisible(thisNode)) {
			return;
		}

		final GraphObject details = renderContext.getDetailsDataObject();
		final boolean detailMode = details != null;

		if (detailMode && thisNode.hideOnDetail()) {
			return;
		}

		if (!detailMode && thisNode.hideOnIndex()) {
			return;
		}

		final EditMode editMode = renderContext.getEditMode(securityContext.getUser(false));

		if (EditMode.RAW.equals(editMode) || EditMode.WIDGET.equals(editMode) || EditMode.DEPLOYMENT.equals(editMode)) {

			thisNode.renderContent(renderContext, depth);

		} else {

			final String subKey = thisNode.getDataKey();

			if (StringUtils.isNotBlank(subKey)) {

				final GraphObject currentDataNode = renderContext.getDataObject();

				// fetch (optional) list of external data elements
				final Iterable<GraphObject> listData = checkListSources(thisNode, securityContext, renderContext);

				final PropertyKey propertyKey;

				if (thisNode.renderDetails() && detailMode) {

					renderContext.setDataObject(details);
					renderContext.putDataObject(subKey, details);

					thisNode.renderContent(renderContext, depth);

				} else {

					if (Iterables.isEmpty(listData) && currentDataNode != null) {

						// There are two alternative ways of retrieving sub elements:
						// First try to get generic properties,
						// if that fails, try to create a propertyKey for the subKey
						final Object elements = currentDataNode.getProperty(new GenericProperty(subKey));

						renderContext.setRelatedProperty(new GenericProperty(subKey));
						renderContext.setSourceDataObject(currentDataNode);

						if (elements != null) {

							if (elements instanceof Iterable) {

								for (Object o : (Iterable)elements) {

									if (o instanceof GraphObject) {

										GraphObject graphObject = (GraphObject)o;
										renderContext.putDataObject(subKey, graphObject);
										thisNode.renderContent(renderContext, depth);

									}
								}

							}

						} else {

							propertyKey = StructrApp.key(currentDataNode.getClass(), subKey);
							renderContext.setRelatedProperty(propertyKey);

							if (propertyKey != null) {

								final Object value = currentDataNode.getProperty(propertyKey);
								if (value != null) {

									if (value instanceof Iterable) {

										for (final Object o : ((Iterable)value)) {

											if (o instanceof GraphObject) {

												renderContext.putDataObject(subKey, (GraphObject)o);
												thisNode.renderContent(renderContext, depth);

											}
										}
									}
								}
							}

						}

						// reset data node in render context
						renderContext.setDataObject(currentDataNode);
						renderContext.setRelatedProperty(null);

					} else {

						renderContext.setListSource(listData);
						thisNode.renderNodeList(securityContext, renderContext, depth, subKey);

					}

				}

			} else {

				thisNode.renderContent(renderContext, depth);
			}
		}
	}

	public static Iterable<GraphObject> checkListSources(final DOMNode thisNode, final SecurityContext securityContext, final RenderContext renderContext) {

		// try registered data sources first
		for (GraphDataSource<Iterable<GraphObject>> source : listSources) {

			try {

				Iterable<GraphObject> graphData = source.getData(renderContext, thisNode);
				if (graphData != null && !Iterables.isEmpty(graphData)) {
					return graphData;
				}

			} catch (FrameworkException fex) {

				logger.warn("", fex);

				logger.warn("Could not retrieve data from graph data source {}: {}", new Object[]{source, fex});
			}
		}

		return Collections.EMPTY_LIST;
	}

	public static Node doAdopt(final DOMNode thisNode, final Page _page) throws DOMException {

		if (_page != null) {

			try {

				thisNode.setOwnerDocument(_page);

			} catch (FrameworkException fex) {

				throw new DOMException(DOMException.INVALID_STATE_ERR, fex.getMessage());

			}
		}

		return thisNode;
	}

	public static Node insertBefore(final DOMNode thisNode, final Node newChild, final Node refChild) throws DOMException {

		// according to DOM spec, insertBefore with null refChild equals appendChild
		if (refChild == null) {

			return thisNode.appendChild(newChild);
		}

		thisNode.checkWriteAccess();

		thisNode.checkSameDocument(newChild);
		thisNode.checkSameDocument(refChild);

		thisNode.checkHierarchy(newChild);
		thisNode.checkHierarchy(refChild);

		if (newChild instanceof DocumentFragment) {

			// When inserting document fragments, we must take
			// care of the special case that the nodes already
			// have a NEXT_LIST_ENTRY relationship coming from
			// the document fragment, so we must first remove
			// the node from the document fragment and then
			// add it to the new parent.
			final DocumentFragment fragment = (DocumentFragment)newChild;
			Node currentChild = fragment.getFirstChild();

			while (currentChild != null) {

				// save next child in fragment list for later use
				Node savedNextChild = currentChild.getNextSibling();

				// remove child from document fragment
				fragment.removeChild(currentChild);

				// insert child into new parent
				thisNode.insertBefore(currentChild, refChild);

				// next
				currentChild = savedNextChild;
			}

		} else {

			final Node _parent = newChild.getParentNode();
			if (_parent != null) {

				_parent.removeChild(newChild);
			}

			try {

				// do actual tree insertion here
				thisNode.treeInsertBefore((DOMNode)newChild, (DOMNode)refChild);

			} catch (FrameworkException frex) {

				if (frex.getStatus() == 404) {

					throw new DOMException(DOMException.NOT_FOUND_ERR, frex.getMessage());

				} else {

					throw new DOMException(DOMException.INVALID_STATE_ERR, frex.getMessage());
				}
			}

			// allow parent to set properties in new child
			thisNode.handleNewChild(newChild);
		}

		return refChild;
	}

	public static Node replaceChild(final DOMNode thisNode, final Node newChild, final Node oldChild) throws DOMException {

		thisNode.checkWriteAccess();

		thisNode.checkSameDocument(newChild);
		thisNode.checkSameDocument(oldChild);

		thisNode.checkHierarchy(newChild);
		thisNode.checkHierarchy(oldChild);

		if (newChild instanceof DocumentFragment) {

			// When inserting document fragments, we must take
			// care of the special case that the nodes already
			// have a NEXT_LIST_ENTRY relationship coming from
			// the document fragment, so we must first remove
			// the node from the document fragment and then
			// add it to the new parent.
			// replace indirectly using insertBefore and remove
			final DocumentFragment fragment = (DocumentFragment)newChild;
			Node currentChild = fragment.getFirstChild();

			while (currentChild != null) {

				// save next child in fragment list for later use
				final Node savedNextChild = currentChild.getNextSibling();

				// remove child from document fragment
				fragment.removeChild(currentChild);

				// add child to new parent
				thisNode.insertBefore(currentChild, oldChild);

				// next
				currentChild = savedNextChild;
			}

			// finally, remove reference element
			thisNode.removeChild(oldChild);

		} else {

			Node _parent = newChild.getParentNode();
			if (_parent != null && _parent instanceof DOMNode) {

				_parent.removeChild(newChild);
			}

			try {
				// replace directly
				thisNode.treeReplaceChild((DOMNode)newChild, (DOMNode)oldChild);

			} catch (FrameworkException frex) {

				if (frex.getStatus() == 404) {

					throw new DOMException(DOMException.NOT_FOUND_ERR, frex.getMessage());

				} else {

					throw new DOMException(DOMException.INVALID_STATE_ERR, frex.getMessage());
				}
			}

			// allow parent to set properties in new child
			thisNode.handleNewChild(newChild);
		}

		return oldChild;
	}

	/*
	@Override
	public boolean onCreation(final SecurityContext securityContext, final ErrorBuffer errorBuffer) throws FrameworkException {

		if (super.onCreation(securityContext, errorBuffer)) {

			return checkName(errorBuffer);
		}

		return false;
	}

	@Override
	public boolean onModification(SecurityContext securityContext, ErrorBuffer errorBuffer, final ModificationQueue modificationQueue) throws FrameworkException {

		if (super.onModification(securityContext, errorBuffer, modificationQueue)) {

			if (customProperties != null) {

				// invalidate data property cache
				customProperties.clear();
			}


			try {

				increasePageVersion();

			} catch (FrameworkException ex) {

				logger.warn("Updating page version failed", ex);

			}

			return checkName(errorBuffer);
		}

		return false;
	}
	*/

	/**
	 * Get all ancestors of this node
	 *
	 * @return list of ancestors
	 */
	static List<Node> getAncestors(final DOMNode thisNode) {

		List<Node> ancestors = new ArrayList<>();

		Node _parent = thisNode.getParentNode();
		while (_parent != null) {

			ancestors.add(_parent);
			_parent = _parent.getParentNode();
		}

		return ancestors;
	}

	/**
	 * Increase version of the page.
	 *
	 * A {@link Page} is a {@link DOMNode} as well, so we have to check 'this' as well.
	 *
	 * @throws FrameworkException
	 */
	static void increasePageVersion(final DOMNode thisNode) throws FrameworkException {

		Page page = null;

		if (thisNode instanceof Page) {

			page = (Page)thisNode;

		} else {

			// ignore page-less nodes
			if (thisNode.getParent() == null) {
				return;
			}
		}

		if (page == null) {

			final List<Node> ancestors = DOMNode.getAncestors(thisNode);
			if (!ancestors.isEmpty()) {

				final DOMNode rootNode = (DOMNode)ancestors.get(ancestors.size() - 1);
				if (rootNode instanceof Page) {

					page = (Page)rootNode;

				} else {

					DOMNode.increasePageVersion(rootNode);
				}

			} else {

				final List<DOMNode> _syncedNodes = thisNode.getSyncedNodes();
				for (final DOMNode syncedNode : _syncedNodes) {

					DOMNode.increasePageVersion(syncedNode);
				}
			}

		}

		if (page != null) {

			page.increaseVersion();

		}

	}

	/*
	protected boolean avoidWhitespace() {

		return false;

	}

	/**
	 * Decide whether this node should be displayed for the given conditions string.
	 *
	 * @param renderContext
	 * @return true if node should be displayed
	protected boolean displayForConditions(final RenderContext renderContext) {

		// In raw or widget mode, render everything
		EditMode editMode = renderContext.getEditMode(securityContext.getUser(false));
		if (EditMode.DEPLOYMENT.equals(editMode) || EditMode.RAW.equals(editMode) || EditMode.WIDGET.equals(editMode)) {
			return true;
		}

		String _showConditions = getProperty(DOMNode.showConditions);
		String _hideConditions = getProperty(DOMNode.hideConditions);

		// If both fields are empty, render node
		if (StringUtils.isBlank(_hideConditions) && StringUtils.isBlank(_showConditions)) {
			return true;
		}
		try {
			// If hide conditions evaluate to "true", don't render
			if (StringUtils.isNotBlank(_hideConditions) && Boolean.TRUE.equals(Scripting.evaluate(renderContext, this, "${".concat(_hideConditions).concat("}"), "hide condition"))) {
				return false;
			}

		} catch (UnlicensedException|FrameworkException ex) {
			logger.error("Hide conditions " + _hideConditions + " could not be evaluated.", ex);
		}
		try {
			// If show conditions evaluate to "false", don't render
			if (StringUtils.isNotBlank(_showConditions) && Boolean.FALSE.equals(Scripting.evaluate(renderContext, this, "${".concat(_showConditions).concat("}"), "show condition"))) {
				return false;
			}

		} catch (UnlicensedException|FrameworkException ex) {
			logger.error("Show conditions " + _showConditions + " could not be evaluated.", ex);
		}

		return true;

	}

	// ----- interface org.w3c.dom.Node -----
	@Override
	public String getTextContent() throws DOMException {

		final DOMNodeList results = new DOMNodeList();
		final TextCollector textCollector = new TextCollector();

		collectNodesByPredicate(this, results, textCollector, 0, false);

		return textCollector.getText();
	}

	@Override
	public void setTextContent(String textContent) throws DOMException {
		// TODO: implement?
	}

	@Override
	public Node getParentNode() {
		// FIXME: type cast correct here?
		return (Node)getProperty(parent);
	}

	@Override
	public NodeList getChildNodes() {
		checkReadAccess();
		return new DOMNodeList(treeGetChildren());
	}

	@Override
	public Node getFirstChild() {
		checkReadAccess();
		return treeGetFirstChild();
	}

	@Override
	public Node getLastChild() {
		return treeGetLastChild();
	}

	@Override
	public Node getPreviousSibling() {
		return listGetPrevious(this);
	}

	@Override
	public Node getNextSibling() {
		return listGetNext(this);
	}

	@Override
	public Document getOwnerDocument() {
		return getProperty(ownerDocument);
	}

	@Override
	public boolean hasChildNodes() {
		return !getProperty(children).isEmpty();
	}

	@Override
	public boolean isSupported(String string, String string1) {
		return false;
	}

	@Override
	public String getNamespaceURI() {
		return null; //return "http://www.w3.org/1999/xhtml";
	}

	@Override
	public String getPrefix() {
		return null;
	}

	@Override
	public void setPrefix(String prefix) throws DOMException {
	}

	@Override
	public String getBaseURI() {
		return null;
	}

	@Override
	public short compareDocumentPosition(Node node) throws DOMException {
		return 0;
	}

	@Override
	public String lookupPrefix(String string) {
		return null;
	}

	@Override
	public boolean isDefaultNamespace(String string) {
		return true;
	}

	@Override
	public String lookupNamespaceURI(String string) {
		return null;
	}

	@Override
	public boolean isEqualNode(Node node) {
		return equals(node);
	}

	@Override
	public Object getFeature(String string, String string1) {
		return null;
	}

	@Override
	public Object setUserData(String string, Object o, UserDataHandler udh) {
		return null;
	}

	@Override
	public Object getUserData(String string) {
		return null;
	}

	// ----- interface DOMAdoptable -----

	public static GraphObjectMap extractHeaders(final Header[] headers) {

		final GraphObjectMap map = new GraphObjectMap();

		for (final Header header : headers) {

			map.put(new StringProperty(header.getName()), header.getValue());
		}

		return map;
	}

	// ----- interface Syncable -----
	@Override
	public List<GraphObject> getSyncData() throws FrameworkException {

		final List<GraphObject> data = super.getSyncData();

		// nodes
		data.addAll(getProperty(DOMNode.children));

		final DOMNode sibling = getProperty(DOMNode.nextSibling);
		if (sibling != null) {

			data.add(sibling);
		}

		// relationships
		for (final DOMChildren child : getOutgoingRelationships(DOMChildren.class)) {
			data.add(child);
		}

		final DOMSiblings siblingRel = getOutgoingRelationship(DOMSiblings.class);
		if (siblingRel != null) {

			data.add(siblingRel);
		}

		// for template nodes
		data.add(getProperty(DOMNode.sharedComponent));
		data.add(getIncomingRelationship(Sync.class));

		// add parent page
		data.add(getProperty(ownerDocument));
		data.add(getOutgoingRelationship(PageLink.class));

		// add parent element
		data.add(getProperty(DOMNode.parent));
		data.add(getIncomingRelationship(DOMChildren.class));

		return data;
	}

	protected static class TagPredicate implements Predicate<Node> {

		private String tagName = null;

		public TagPredicate(String tagName) {
			this.tagName = tagName;
		}

		@Override
		public boolean accept(Node obj) {

			if (obj instanceof DOMElement) {

				DOMElement elem = (DOMElement)obj;

				if (tagName.equals(elem.getProperty(DOMElement.tag))) {
					return true;
				}
			}

			return false;
		}
	}

	private String cachedPagePath = null;

	*/

	public static String getPagePath(final DOMNode thisNode) {

		String cachedPagePath = (String)thisNode.getTemporaryStorage().get("cachedPagePath");
		if (cachedPagePath == null) {

			final StringBuilder buf = new StringBuilder();
			DOMNode current         = thisNode;

			while (current != null) {

				buf.insert(0, "/" + current.getContextName());
				current = current.getParent();
			}

			cachedPagePath = buf.toString();

			thisNode.getTemporaryStorage().put("cachedPagePath", cachedPagePath);
		}

		return cachedPagePath;
	}

	static void checkName(final DOMNode thisNode, final ErrorBuffer errorBuffer) {

		final String _name = thisNode.getProperty(AbstractNode.name);
		if (_name != null && _name.contains("/")) {

			errorBuffer.add(new SemanticErrorToken(thisNode.getType(), AbstractNode.name, "may_not_contain_slashes", _name));
		}
	}

	/*

	public void setVisibility(final boolean publicUsers, final boolean authenticatedUsers) throws FrameworkException {

		final PropertyMap map = new PropertyMap();

		map.put(NodeInterface.visibleToPublicUsers, publicUsers);
		map.put(NodeInterface.visibleToAuthenticatedUsers, authenticatedUsers);

		setProperties(securityContext, map);
	}

	// ----- private methods -----
	*/

	// ----- nested classes -----
	static class TextCollector implements Predicate<Node> {

		private final StringBuilder textBuffer = new StringBuilder(200);

		@Override
		public boolean accept(final Node obj) {

			if (obj instanceof Text) {
				textBuffer.append(((Text)obj).getTextContent());
			}

			return false;
		}

		public String getText() {
			return textBuffer.toString();
		}
	}
}
