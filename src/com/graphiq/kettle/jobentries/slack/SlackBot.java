/*! ******************************************************************************
 *
 * Pentaho Data Integration
 *
 * Copyright (C) 2002-2013 by Pentaho : http://www.pentaho.com
 *
 *******************************************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package com.graphiq.kettle.jobentries.slack;


import java.net.ConnectException;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import org.pentaho.di.cluster.SlaveServer;
import org.pentaho.di.core.Const;
import org.pentaho.di.core.Result;
import org.pentaho.di.core.annotations.JobEntry;
import org.pentaho.di.core.database.DatabaseMeta;
import org.pentaho.di.core.encryption.Encr;
import org.pentaho.di.core.exception.KettleDatabaseException;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.exception.KettleXMLException;
import org.pentaho.di.core.xml.XMLHandler;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.job.entry.JobEntryBase;
import org.pentaho.di.job.entry.JobEntryInterface;
import org.pentaho.di.repository.ObjectId;
import org.pentaho.di.repository.Repository;
import org.pentaho.metastore.api.IMetaStore;
import org.w3c.dom.Node;
import static com.graphiq.kettle.jobentries.slack.BotIcons.botIcons;

/**
 * This defines a job entry that allows you to post a message to a Slack channel or group
 *
 * @author Andrew Overton / Matt Rybak
 * @since 08-28-2015
 *
 */
@JobEntry(
        id = "SlackBotJobEntry",
        image = "com/graphiq/kettle/jobentries/slack/resources/icon.png",
        i18nPackageName="com.graphiq.kettle.jobentries.slack",
        name="SlackBotJobEntry.Name",
        description = "SlackBotJobEntry.TooltipDesc",
        categoryDescription="SlackBotJobEntry.Category"
)
public class SlackBot extends JobEntryBase implements Cloneable, JobEntryInterface{

    /**
     *	The PKG member is used when looking up internationalized strings.
     *	The properties file with localized keys is expected to reside in
     *	{the package of the class specified}/messages/messages_{locale}.properties
     */
    private static Class<?> PKG = SlackBot.class; // for i18n purposes $NON-NLS-1$

    // This field holds the configured result of the job entry.
    // It is configured in the SlackBotDialog
    private String selectedChannel, customText, postType, token, botName, botIcon;
    private boolean successMsg, failureMsg, customMsg, alert;
    private LinkedList<String> channelList;
    private final String defaultName = "Bot", defaultIcon = ":pig:";

    /**
     * The JobEntry constructor executes super() and initializes its fields
     * with sensible defaults for new instances of the job entry.
     *
     * @param name the name of the new job entry
     */
    public SlackBot(String name){
        super(name, "");

        // the default is to generate a positive outcome
        selectedChannel = "";
        token = "";
        postType = "Channel";
        botName = defaultName;
        botIcon = defaultIcon;
        successMsg = true;
        failureMsg = false;
        customMsg = false;
        alert = false;
        customText = "Custom text goes here...";
        channelList = new LinkedList<String>();
    }
    /**
     * No-Arguments constructor for convenience purposes.
     */
    public SlackBot(){
        this("");
    }

    /**
     * Let PDI know the class name to use for the dialog.
     * @return the class name to use for the dialog for this job entry
     */
    public String getDialogClassName(){
        return this.getClass().getCanonicalName()+"Dialog";
    }

    /**
     * This method is used when a job entry is duplicated in Spoon. It needs to return a deep copy of this
     * job entry object. Be sure to create proper deep copies if the job entry configuration is stored in
     * modifiable objects.
     *
     * See org.pentaho.di.trans.steps.rowgenerator.RowGeneratorMeta.clone() for an example on creating
     * a deep copy of an object.
     *
     * @return a deep copy of this
     */
    public Object clone(){
        SlackBot retval = (SlackBot) super.clone();
        retval.channelList = new LinkedList<String>();
        ListIterator<String> listIterator = channelList.listIterator();
        for (String channel : channelList) {
            retval.channelList.add(channel);
        }
        return retval;
    }

    /**
     * This method is called by Spoon when a job entry needs to serialize its configuration to XML. The expected
     * return value is an XML fragment consisting of one or more XML tags.
     *
     * Please use org.pentaho.di.core.xml.XMLHandler to conveniently generate the XML.
     *
     * Note: the returned string must include the output of super.getXML() as well
     * @return a string containing the XML serialization of this job entry
     */
    @Override
    public String getXML(){
        StringBuffer retval = new StringBuffer();

        retval.append(super.getXML());
        retval.append("      ").append(XMLHandler.addTagValue("token", Encr.encryptPasswordIfNotUsingVariables(token)));
        retval.append("      ").append(XMLHandler.addTagValue("selectedChannel", selectedChannel));
        retval.append("      ").append(XMLHandler.addTagValue("botName", botName));
        retval.append("      ").append(XMLHandler.addTagValue("botIcon", botIcon));
        retval.append("      ").append(XMLHandler.addTagValue("alert", alert));
        retval.append("      ").append(XMLHandler.addTagValue("customMsg", customMsg));
        retval.append("      ").append(XMLHandler.addTagValue("successMsg", successMsg));
        retval.append("      ").append(XMLHandler.addTagValue("failureMsg", failureMsg));
        retval.append("      ").append(XMLHandler.addTagValue("customText", customText));
        retval.append("      <channels>" + Const.CR);
        for (String channel : channelList) {
            retval.append("        ").append(XMLHandler.addTagValue("channel", channel));
        }
        retval.append("      </channels>" + Const.CR);
        return retval.toString();
    }

    /**
     * This method is called by PDI when a job entry needs to load its configuration from XML.
     *
     * Please use org.pentaho.di.core.xml.XMLHandler to conveniently read from the
     * XML node passed in.
     *
     * Note: the implementation must call super.loadXML() to ensure correct behavior
     *
     * @param entrynode		the XML node containing the configuration
     * @param databases		the databases available in the job
     * @param slaveServers	the slave servers available in the job
     * @param rep			the repository connected to, if any
     * @param metaStore		the metastore to optionally read from
     */
    @Override
    public void loadXML(Node entrynode, List<DatabaseMeta> databases, List<SlaveServer> slaveServers, Repository rep, IMetaStore metaStore) throws KettleXMLException {

        try{
            super.loadXML(entrynode, databases, slaveServers);
            setToken(Encr.decryptPasswordOptionallyEncrypted(XMLHandler.getTagValue(entrynode, "token")));
            setSelectedChannel(XMLHandler.getTagValue(entrynode, "selectedChannel"));
            botName = XMLHandler.getTagValue(entrynode, "botName");
            botIcon = XMLHandler.getTagValue(entrynode, "botIcon");
            alert = "Y".equalsIgnoreCase(XMLHandler.getTagValue(entrynode, "alert"));
            successMsg = "Y".equalsIgnoreCase(XMLHandler.getTagValue(entrynode, "successMsg"));
            failureMsg = "Y".equalsIgnoreCase(XMLHandler.getTagValue(entrynode, "failureMsg"));
            customMsg = "Y".equalsIgnoreCase(XMLHandler.getTagValue(entrynode, "customMsg"));
            setCustomText(XMLHandler.getTagValue(entrynode, "customText"));
            // populate channel list
            Node channels = XMLHandler.getSubNode( entrynode, "channels" );
            int count = XMLHandler.countNodes( channels, "channel" );
            channelList = new LinkedList<String>();
            for ( int i = 0; i < count; i++ ) {
                Node fnode = XMLHandler.getSubNodeByNr( channels, "channel", i );
                channelList.add(fnode.getNodeValue());
            }
        }
        catch(Exception e){
            throw new KettleXMLException(BaseMessages.getString(PKG, "Demo.Error.UnableToLoadFromXML"), e);
        }
    }

    /**
     * This method is called by Spoon when a job entry needs to serialize its configuration to a repository.
     * The repository implementation provides the necessary methods to save the job entry attributes.
     *
     * @param rep		the repository to save to
     * @param id_job	the id to use for the job when saving
     * @param metaStore		the metastore to optionally write to
     */
    @Override
    public void saveRep(Repository rep, IMetaStore metaStore, ObjectId id_job) throws KettleException{

        try{
            rep.saveJobEntryAttribute(id_job, getObjectId(), "selectedChannel", selectedChannel);
            rep.saveJobEntryAttribute(id_job, getObjectId(), "token", Encr.encryptPasswordIfNotUsingVariables(token));
            rep.saveJobEntryAttribute(id_job, getObjectId(), "postType", postType);
            rep.saveJobEntryAttribute(id_job, getObjectId(), "botName", botName);
            rep.saveJobEntryAttribute(id_job, getObjectId(), "successMsg", successMsg);
            rep.saveJobEntryAttribute(id_job, getObjectId(), "failureMsg", failureMsg);
            rep.saveJobEntryAttribute(id_job, getObjectId(), "customMsg", customMsg);
            rep.saveJobEntryAttribute(id_job, getObjectId(), "customText", customText);
        }
        catch(KettleDatabaseException dbe){
            throw new KettleException(BaseMessages.getString(PKG, "SlackBot.RepoSaveError")+id_job, dbe);
        }
    }

    /**
     * This method is called by PDI when a job entry needs to read its configuration from a repository.
     * The repository implementation provides the necessary methods to read the job entry attributes.
     *
     * @param rep			the repository to read from
     * @param metaStore		the metastore to optionally read from
     * @param id_jobentry	the id of the job entry being read
     * @param databases		the databases available in the job
     * @param slaveServers	the slave servers available in the job
     */
    @Override
    public void loadRep(Repository rep, IMetaStore metaStore, ObjectId id_jobentry, List<DatabaseMeta> databases, List<SlaveServer> slaveServers) throws KettleException{
        try{
            selectedChannel = rep.getJobEntryAttributeString(id_jobentry, "selectedChannel");
            token = Encr.decryptPasswordOptionallyEncrypted(rep.getJobEntryAttributeString(id_jobentry, "token"));
            postType = rep.getJobEntryAttributeString(id_jobentry, "postType");
            botName = rep.getJobEntryAttributeString(id_jobentry, "botName");
            successMsg = rep.getJobEntryAttributeBoolean(id_jobentry, "successMsg");
            failureMsg = rep.getJobEntryAttributeBoolean(id_jobentry, "failureMsg");
            customMsg = rep.getJobEntryAttributeBoolean(id_jobentry, "customMsg");
            customText = rep.getJobEntryAttributeString(id_jobentry, "customText");
        }
        catch(KettleDatabaseException dbe){
            throw new KettleException(BaseMessages.getString(PKG, "SlackBot.RepoLoadError")+id_jobentry, dbe);
        }
    }

    /**
     * This method is called when it is the job entry's turn to run during the execution of a job.
     * It should return the passed in Result object, which has been updated to reflect the outcome
     * of the job entry. The execute() method should call setResult(), setNrErrors() and modify the
     * rows or files attached to the result object if required.
     *
     * @param prev_result The result of the previous execution
     * @param nr Distance of job entry from start entry job
     * @return The Result of the execution.
     */
    public Result execute(Result prev_result, int nr){
        int errors = 0;
        boolean outcome = true;
        try {
            String resolvedToken = environmentSubstitute(token);
            if (isDebug()) {
                logDebug("Token is: " + resolvedToken);
            }
            SlackConnection slack = new SlackConnection(resolvedToken);
            if (!slack.getAuthStatus()) {
                throw new ConnectException(BaseMessages.getString(PKG, "SlackBot.AuthError"));
            }
            String msg;
            if (successMsg) {
                msg = BaseMessages.getString(PKG, "SlackBot.StandardSuccess");
            } else if (failureMsg) {
                msg = BaseMessages.getString(PKG, "SlackBot.StandardFailure");
            } else {
                msg = environmentSubstitute(customText);
            }
            logBasic("Sending to slack");
            if (isDebug()) {
                logBasic(slack.toString());
            }
            String resolvedChannel = environmentSubstitute(selectedChannel);
            boolean result = slack.postToSlack(resolvedChannel, msg, botName, botIcon);
            if (!result) {
                throw new ConnectException(BaseMessages.getString(PKG, "SlackBot.PostError"));
            }
            // indicate there are no errors
            prev_result.setNrErrors(errors);
        } catch (Exception e) {
            logError(e.getMessage());
            errors++;
            outcome = false;
        }
        // indicate the result as configured
        prev_result.setResult(outcome);
        return prev_result;
    }

    /**
     * Returns true if the job entry offers a genuine true/false result upon execution,
     * and thus supports separate "On TRUE" and "On FALSE" outgoing hops.
     */
    public boolean evaluates(){
        return true;
    }

    /**
     * Returns true if the job entry supports unconditional outgoing hops.
     */
    public boolean isUnconditional(){
        return false;
    }

    public String getSelectedChannel() {
        return selectedChannel;
    }

    public void setSelectedChannel(String selectedChannel) {
        this.selectedChannel = Const.NVL(selectedChannel, "");
    }

    public String getCustomText() {
        return customText;
    }

    public void setCustomText(String customText) {
        this.customText = Const.NVL(customText, "");
    }

    public boolean isSuccessMsg() {
        return successMsg;
    }

    public void setSuccessMsg(boolean successMsg) {
        this.successMsg = successMsg;
    }

    public boolean isFailureMsg() {
        return failureMsg;
    }

    public void setFailureMsg(boolean failureMsg) {
        this.failureMsg = failureMsg;
    }

    public boolean isCustomMsg() {
        return customMsg;
    }

    public void setCustomMsg(boolean customMsg) {
        this.customMsg = customMsg;
    }

    public boolean isAlert() {
        return alert;
    }

    public void setAlert(boolean alert) {
        this.alert = alert;
    }

    public String getPostType() {
        return postType;
    }

    public void setPostType(String postType) {
        this.postType = postType;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = Const.NVL(token, "");
    }

    public String getBotName() {
        return botName;
    }

    public void setBotName(String botName) {
        this.botName = Const.NVL(botName, defaultName);
    }

    public String getBotIcon() {
        return botIcon;
    }

    public void setBotIcon(String botIcon) {
        if (!botIcon.startsWith(":")) {
            botIcon = ":" + botIcon;
        }
        if (!botIcon.endsWith(":")) {
            botIcon = botIcon + ":";
        }
        this.botIcon = botIcon;

    }

    public Set<String> getIconList() {
        return botIcons;
    }
}

