/**
 * Copyright 2008 The University of North Carolina at Chapel Hill
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cdr.forms;

import gov.loc.mets.AgentType;
import gov.loc.mets.DivType;
import gov.loc.mets.FLocatType;
import gov.loc.mets.FileGrpType;
import gov.loc.mets.FileGrpType1;
import gov.loc.mets.FileSecType;
import gov.loc.mets.FileType;
import gov.loc.mets.FptrType;
import gov.loc.mets.LOCTYPEType;
import gov.loc.mets.MDTYPEType;
import gov.loc.mets.MdSecType;
import gov.loc.mets.MdWrapType;
import gov.loc.mets.MetsFactory;
import gov.loc.mets.MetsHdrType;
import gov.loc.mets.MetsPackage;
import gov.loc.mets.MetsType;
import gov.loc.mets.MetsType1;
import gov.loc.mets.ROLEType;
import gov.loc.mets.StructMapType;
import gov.loc.mets.TYPEType;
import gov.loc.mets.XmlDataType1;
import gov.loc.mets.impl.MetsFactoryImpl;
import gov.loc.mets.util.METSConstants;
import gov.loc.mets.util.MetsResourceFactoryImpl;
import gov.loc.mods.mods.DocumentRoot;
import gov.loc.mods.mods.MODSFactory;
import gov.loc.mods.mods.MODSPackage;
import gov.loc.mods.mods.ModsDefinition;
import gov.loc.mods.mods.util.MODSResourceFactoryImpl;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.security.Principal;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import org.apache.abdera.Abdera;
import org.apache.abdera.factory.Factory;
import org.apache.abdera.model.Document;
import org.apache.abdera.model.Entry;
import org.apache.abdera.model.Text.Type;
import org.apache.abdera.parser.Parser;
import org.apache.abdera.parser.stax.FOMExtensibleElement;
import org.apache.commons.httpclient.methods.multipart.ByteArrayPartSource;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.FeatureMap;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xml.type.internal.XMLCalendar;
import org.jdom.Element;
import org.jdom.Namespace;
import org.jdom.output.XMLOutputter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.ui.ModelMap;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import org.w3._1999.xlink.XlinkPackage;

import cdr.forms.DepositResult.Status;

import com.philvarner.clamavj.ClamScan;
import com.philvarner.clamavj.ScanResult;

import crosswalk.Form;
import crosswalk.FormElement;
import crosswalk.MetadataBlock;
import crosswalk.OutputElement;

@Controller
@RequestMapping(value = { "/*", "/**" })
@SessionAttributes("form")
public class FormController {
	ResourceSet rs = null;
	
	
	@Autowired
	ClamScan clamScan = null;
	
	public ClamScan getClamScan() {
		return clamScan;
	}

	public void setClamScan(ClamScan clamScan) {
		this.clamScan = clamScan;
	}

	public FormController() {
		rs = new ResourceSetImpl();
		rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("mods", new MODSResourceFactoryImpl());
		rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("mets", new MetsResourceFactoryImpl());
		rs.getPackageRegistry().put(MODSPackage.eNS_URI, MODSPackage.eINSTANCE);
		rs.getPackageRegistry().put(MetsPackage.eNS_URI, MetsPackage.eINSTANCE);
		rs.getPackageRegistry().put(XlinkPackage.eNS_URI, XlinkPackage.eINSTANCE);
		LOG.debug("FormController created");
	}

	private static final Logger LOG = LoggerFactory.getLogger(FormController.class);
	
	@Autowired
	private DepositHandler depositHandler;
	
	public DepositHandler getDepositHandler() {
		return depositHandler;
	}

	public void setDepositHandler(DepositHandler depositHandler) {
		this.depositHandler = depositHandler;
	}
	
	@Autowired
	public String administratorEmail = null;

	public String getAdministratorEmail() {
		return administratorEmail;
	}

	public void setAdministratorEmail(String administratorEmail) {
		this.administratorEmail = administratorEmail;
	}

	@Autowired
	AbstractFormFactory factory = null;

	public AbstractFormFactory getFactory() {
		return factory;
	}

	public void setFactory(AbstractFormFactory factory) {
		this.factory = factory;
	}
	
	@Autowired
	private AuthorizationHandler authorizationHandler = null;

	public AuthorizationHandler getAuthorizationHandler() {
		return authorizationHandler;
	}

	public void setAuthorizationHandler(AuthorizationHandler authorizationHandler) {
		this.authorizationHandler = authorizationHandler;
	}
	
	@Autowired
	private NotificationHandler notificationHandler = null;

	public NotificationHandler getNotificationHandler() {
		return notificationHandler;
	}

	public void setNotificationHandler(NotificationHandler notificationHandler) {
		this.notificationHandler = notificationHandler;
	}

	@InitBinder
   protected void initBinder(WebDataBinder binder) {
       binder.setValidator(new FormValidator());
       binder.registerCustomEditor(java.util.Date.class, new DateEditor());
       //binder.setBindEmptyMultipartFiles(false);
       binder.registerCustomEditor(java.lang.String.class, new StringCleanerTrimmerEditor(true));
   }
	
//	@ModelAttribute("form")
//	protected Form getForm(@PathVariable String formId) {
//		return factory.getForm(formId);
//	}
	
	// get always loads the form model into modelmap as just "form"
	// also sets modelmap "formId"
	// successful save will destroy the specific model?

	@RequestMapping(value = "/{formId}.form", method = RequestMethod.GET)
	public String showForm(@PathVariable String formId, ModelMap modelmap, HttpServletRequest request) throws PermissionDeniedException {
		LOG.debug("in GET for form " + formId);
		String sessionFormId = (String)modelmap.get("formId");
		Form form = null;
		if(sessionFormId == null || !sessionFormId.equals(formId)) {
			form = factory.getForm(formId);
			if(form == null) {
				return "404";
			}
			this.getAuthorizationHandler().checkPermission(formId, form, request);
			modelmap.put("form", form);
			modelmap.put("formId", formId);
		}
		return "form";
	}

	@RequestMapping(value = "/{formId}.form", method = RequestMethod.POST)
	public String processForm(@PathVariable String formId, @Valid @ModelAttribute("form") Form form, BindingResult errors,
			Principal user, @RequestParam("file") MultipartFile mpfile, SessionStatus sessionStatus, HttpServletRequest request) throws PermissionDeniedException {
		
		gov.loc.mods.mods.DocumentRoot modsDocumentRoot;
		String modsXml;
		
		File file;
		String pid;
		String filename;
		String filetype;
		
		Entry entry;
		FilePart atomPart, payloadPart;

		
		try {
			request.setCharacterEncoding("UTF-8");
		} catch (UnsupportedEncodingException e) {
			LOG.error("Failed to set character encoding", e);
		}
		
		if (user != null)
			form.setCurrentUser(user.getName());
		
		this.getAuthorizationHandler().checkPermission(formId, form, request);
		
		
		// Handle uploaded files, exiting to report errors if we find any
		
		file = null;
		filename = null;
		filetype = null;
		
		if (mpfile.isEmpty()) {
			errors.addError(new FieldError("form", "file", "You must select a file for upload."));
		} else {
			file = handleUpload(mpfile);
			filename = mpfile.getOriginalFilename().replaceAll(Pattern.quote("\""), "");
			
			if (mpfile.getContentType() == null)
				filetype = "application/octet-stream";
			else
				filetype = mpfile.getContentType();
			
			String scanResult = virusScan(file);
			if (scanResult != null) {
				errors.addError(new FieldError("form", "file", scanResult));
				file.delete();
				file = null;
			}
		}
		
		if (errors.hasErrors()) {
			LOG.debug(errors.getErrorCount() + " errors");
			return "form";
		}
		
		
		// Create deposit parts
		
		pid = "uuid:" + UUID.randomUUID().toString();
		
		modsDocumentRoot = makeMods(form);
		modsXml = serializeMods(modsDocumentRoot);
		
		entry = makeAtomPubEntry(pid, filename, modsXml, form.isReviewBeforePublication());
		
		// atomPart
		
		try {
			StringWriter writer = new StringWriter();
			entry.writeTo(writer);
			atomPart = new FilePart("atom", new ByteArrayPartSource("atom", writer.toString().getBytes()), "application/atom+xml", "utf-8");
		} catch (IOException e) {
			throw new Error(e);
		}
		
		// payloadPart
		
		try {
			payloadPart = new FilePart("payload", filename, file);
		} catch (FileNotFoundException e) {
			throw new Error(e);
		}
		
		payloadPart.setContentType(filetype);
		payloadPart.setTransferEncoding("binary");
		
		
		// Make the deposit
		
		DepositResult result = this.getDepositHandler().depositMultipart(form.getDepositContainerId(), pid, atomPart, payloadPart);
		
		if (result.getStatus() == Status.FAILED) {
			LOG.error("deposit failed");
			errors.addError(new FieldError("form", "file", "Deposit failed with response code: " + result.getStatus()));
			return "form";
		}
		getNotificationHandler().notifyDeposit(form, result, user.getName());
		
		
		// Clean up: delete the temporary file, clear the session
		
		if (file != null)
			file.delete();
		
		sessionStatus.setComplete();
		request.setAttribute("formId", formId);

		return "success";
		
	}

	private synchronized String virusScan(File depositFile) {
		try {
			ScanResult result = this.getClamScan().scan(new FileInputStream(depositFile));
			switch(result.getStatus()) {
				case PASSED:
					return null;
				case FAILED:
					return "A virus was detected in your file. Please scan your computer for viruses or report this issue to technical support.";
				case ERROR:
					throw new Error("There was a problem running the virus scan.", result.getException());
			}
		} catch (FileNotFoundException e) {
			throw new Error("There was a problem finding the uploaded file: "+depositFile.getName(), e);
		}
		return null;
	}

	private File handleUpload(MultipartFile file) {
		File result = null;
		OutputStream out = null;
		try {
			result = File.createTempFile("form", ".data");
			InputStream stream = file.getInputStream();
			out = new BufferedOutputStream(new FileOutputStream(result));
			for(int i = stream.read(); i >= 0; i = stream.read()) {
				out.write(i);
			}
			out.flush();
		} catch(IOException e) {
			throw new Error(e);
		} finally {
			if(out != null) {
				try {
					out.close();
				} catch(IOException ignored) {}
			}
		}
		return result;
	}

	@ExceptionHandler(PermissionDeniedException.class)
	public ModelAndView handleForbidden(PermissionDeniedException e) {
		ModelAndView modelview = new ModelAndView("403");
		modelview.addObject("formId", e.getFormId());
		modelview.addObject("form", e.getForm());
		modelview.addObject("message", e.getMessage()+" \nSend email to "+this.getAdministratorEmail()+" to request access.");
		return modelview;
	}
	
	private gov.loc.mods.mods.DocumentRoot makeMods(Form form) {
		// run the mapping and get a MODS record. (report any errors)
		ModsDefinition mods = MODSFactory.eINSTANCE.createModsDefinition();
		DocumentRoot root = MODSFactory.eINSTANCE.createDocumentRoot();
		root.setMods(mods);
		for (FormElement fe : form.getElements()) {
			if(MetadataBlock.class.isInstance(fe)) {
				MetadataBlock mb = (MetadataBlock)fe;
				for(OutputElement oe : mb.getElements()) {
					oe.updateRecord(mods);
				}
			}
		}
		return root;
	}
	
	private String serializeMods(gov.loc.mods.mods.DocumentRoot root) {
		
		gov.loc.mods.mods.ModsDefinition mods = root.getMods();
		
		File tmp;
		try {
			tmp = File.createTempFile("tmp", ".mods");
		} catch (IOException e1) {
			throw new Error(e1);
		}
		
		URI uri = URI.createURI(tmp.toURI().toString());
		XMLResource res = (XMLResource) rs.createResource(uri);
		res.getContents().add(root);
		
		StringWriter sw = new StringWriter();
		Map<Object, Object> options = new HashMap<Object, Object>();
		
		options.put(XMLResource.OPTION_ENCODING, "utf-8");
		options.put(XMLResource.OPTION_DECLARE_XML, "");
		options.put(XMLResource.OPTION_LINE_WIDTH, new Integer(80));
		options.put(XMLResource.OPTION_ROOT_OBJECTS, Collections.singletonList(mods));
		
		try {
			res.save(sw, options);
		} catch (IOException e) {
			throw new Error("failed to serialize XML for model object", e);
		}
		return sw.toString();
	}
	
	private gov.loc.mets.DocumentRoot makeMets(gov.loc.mods.mods.DocumentRoot modsDocumentRoot, String filename, String filetype) {
		
		gov.loc.mets.DocumentRoot root = MetsFactory.eINSTANCE.createDocumentRoot();
		root.setMets(MetsFactory.eINSTANCE.createMetsType1());
		MetsType mets = root.getMets();
		
		mets.setPROFILE("http://cdr.unc.edu/METS/profiles/Simple");
		
		// Header
		
		MetsHdrType head = MetsFactory.eINSTANCE.createMetsHdrType();
		Date currentTime = new Date(System.currentTimeMillis());
		head.setCREATEDATE(new XMLCalendar(currentTime, XMLCalendar.DATETIME));
		head.setLASTMODDATE(new XMLCalendar(currentTime, XMLCalendar.DATETIME));
		
		AgentType agent = MetsFactory.eINSTANCE.createAgentType();
		agent.setROLE(ROLEType.CREATOR);
		agent.setTYPE(TYPEType.INDIVIDUAL);
		agent.setName("Someone");
		head.getAgent().add(agent);

		mets.setMetsHdr(head);
		
		// Metadata section
		
		// Blank MODS
		
		MdSecType mdSec = MetsFactory.eINSTANCE.createMdSecType();
		mdSec.setID("mods");
		
		MdWrapType mdWrap = MetsFactory.eINSTANCE.createMdWrapType();
		mdWrap.setMDTYPE(MDTYPEType.MODS);
		
		XmlDataType1 xmlData = MetsFactory.eINSTANCE.createXmlDataType1();

		xmlData.getAny().add(MODSPackage.eINSTANCE.getDocumentRoot_Mods(), modsDocumentRoot.getMods());
		
		mdWrap.setXmlData(xmlData);
		mdSec.setMdWrap(mdWrap);
		
		mets.getDmdSec().add(mdSec);
		
		// Files section
		
		FileSecType fileSec = MetsFactory.eINSTANCE.createFileSecType();
		mets.setFileSec(fileSec);
		
		FileGrpType1 fileGrp = MetsFactory.eINSTANCE.createFileGrpType1();
		
		FileType file = MetsFactory.eINSTANCE.createFileType();
		file.setID("f1");
		file.setMIMETYPE(filetype);
		
		FLocatType fLocat = MetsFactory.eINSTANCE.createFLocatType();
		fLocat.setLOCTYPE(LOCTYPEType.URL);
		fLocat.setHref(filename);

		file.getFLocat().add(fLocat);
		fileGrp.getFile().add(file);
		fileSec.getFileGrp().add(fileGrp);
		
		// Structural map

		StructMapType structMap = MetsFactory.eINSTANCE.createStructMapType();
		
		DivType folderDiv = MetsFactory.eINSTANCE.createDivType();
		folderDiv.setTYPE(METSConstants.Div_Folder);
		folderDiv.getDmdSec().add(mdSec);

		DivType fileDiv = MetsFactory.eINSTANCE.createDivType();
		fileDiv.setTYPE(METSConstants.Div_File);
		FptrType fptr = MetsFactory.eINSTANCE.createFptrType();
		fptr.setFILEID("f1");
		fileDiv.getFptr().add(fptr);
		folderDiv.getDiv().add(fileDiv);

		structMap.setDiv(folderDiv);

		mets.getStructMap().add(structMap);
		
		return root;
		
	}
	
	private String serializeMets(gov.loc.mets.DocumentRoot root) {
		
		gov.loc.mets.MetsType mets = root.getMets();
		
		File tmp;
		try {
			tmp = File.createTempFile("tmp", ".mets");
		} catch (IOException e1) {
			throw new Error(e1);
		}
		
		URI uri = URI.createURI(tmp.toURI().toString());
		XMLResource res = (XMLResource) rs.createResource(uri);
		res.getContents().add(root);
		
		StringWriter sw = new StringWriter();
		Map<Object, Object> options = new HashMap<Object, Object>();
		
		options.put(XMLResource.OPTION_ENCODING, "utf-8");
		options.put(XMLResource.OPTION_DECLARE_XML, "");
		options.put(XMLResource.OPTION_LINE_WIDTH, new Integer(80));
		options.put(XMLResource.OPTION_ROOT_OBJECTS, Collections.singletonList(mets));
		
		try {
			res.save(sw, options);
		} catch (IOException e) {
			throw new Error("failed to serialize XML for model object", e);
		}
		
		return sw.toString();
	}
	
	private Entry makeAtomPubEntry(String pid, String filename, String mods, boolean isReviewBeforePublication) {
		
		Abdera abdera = Abdera.getInstance();
		Factory factory = abdera.getFactory();
		Entry entry = factory.newEntry();
		// id is the identifier of the Atom POST
		entry.setId("urn:uuid:" + UUID.randomUUID().toString());
		entry.setSummary("mods and binary deposit", Type.TEXT);
		entry.setTitle(filename);
		entry.setUpdated(new Date(System.currentTimeMillis()));
		Parser parser = abdera.getParser();
		Document<FOMExtensibleElement> doc = parser.parse(new ByteArrayInputStream(mods.getBytes()));
		entry.addExtension(doc.getRoot());
		
		// If the deposit must be reviewed before publication, add a RELS-EXT triple to block publication

		if (isReviewBeforePublication)
			addPublicationBlockingRELSEXT(entry, pid);
		
		return entry;
		
	}

	/**
	 * Add a RELS-EXT datastream with an entry that blocks publication. (assuming a review work flow)
	 * @param entry
	 * @param pid
	 */
	private void addPublicationBlockingRELSEXT(Entry entry, String pid) {
		Parser parser = Abdera.getInstance().getParser();
		Element dsEl = new Element("datastream", "cdr", "http://cdr.lib.unc.edu/");
		dsEl.setAttribute("id", "RELS-EXT");
		Namespace rdfNS = Namespace.getNamespace("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
		dsEl.addContent(
				new Element("RDF", rdfNS).addContent(
						new Element("Description", rdfNS)
							.setAttribute("about", "info:fedora/"+pid, rdfNS)
							.addContent(
								new Element("isPublished", "cdr-model", "http://cdr.unc.edu/definitions/1.0/base-model.xml#")
									.setText("no")
						)
				)
		);
		String rels = new XMLOutputter().outputString(dsEl);
		Document<FOMExtensibleElement> doc = parser.parse(new ByteArrayInputStream(rels.getBytes()));
		entry.addExtension(doc.getRoot());
	}

}