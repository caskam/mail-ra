/*
 * JBoss, Home of Professional Open Source
 * Copyright 2016, Red Hat, Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.mail.ra;

import java.util.Iterator;
import java.util.Properties;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.NoSuchProviderException;
import javax.mail.Session;
import javax.mail.Store;

/**
 * An encapsulation of a mail store folder used by the MailActivation.run to
 * poll and retrieve new messages.
 *
 * @author <a href="mailto:scott.stark@jboss.org">Scott Stark</a>
 * @author <a href="mailto:jesper.pedersen@jboss.org">Jesper Pedersen</a>
 */
public abstract class MailFolder implements Iterator {
    private Session session;
    private Store store;
    private Folder folder;
    private String mailServer;
    private String folderName;
    private String userName;
    private String password;
    private Integer port;
    private boolean debug = false;
    private boolean starttls = false;
    private Properties sessionProps;

    private Message[] msgs = {};
    private int messagePosition;

    /**
     * Constructor
     *
     * @param spec The mail activation spec
     */
    public MailFolder(MailActivationSpec spec) {
        mailServer = spec.getMailServer();
        folderName = spec.getMailFolder();
        userName = spec.getUserName();
        password = spec.getPassword();
        debug = spec.isDebug();
        starttls = spec.isStarttls();
        port = spec.getPort();

        sessionProps = new Properties();
        sessionProps.setProperty("mail.transport.protocol", "smtp");
        sessionProps.setProperty("mail.smtp.host", mailServer);
        sessionProps.setProperty("mail.debug", debug + "");

        // JavaMail doesn't implement POP3 STARTTLS
        sessionProps.setProperty("mail.imap.starttls.enable", starttls + "");
    }

    /**
     * Open a mail session
     *
     * @throws Exception Thrown if a session can't be established
     */
    public void open() throws Exception {
        // Get a session object
        session = Session.getInstance(sessionProps);
        session.setDebug(debug);
        // Get a store object
        store = openStore(session);
        if (port == 0) {
            store.connect(mailServer, userName, password);
        } else {
            store.connect(mailServer, port, userName, password);
        }
        folder = store.getFolder(folderName);

        if (folder == null || (!this.folder.exists())) {
            throw new MessagingException("Failed to find folder: " + folderName);
        }

        folder.open(Folder.READ_WRITE);
        msgs = getMessages(folder);
    }

    /**
     * Closes the mail session
     *
     * @throws MessagingException Thrown if an error occurs during close
     */
    public void close() throws MessagingException {
        close(true);
    }

    /**
     * {@inheritDoc}
     */
    public boolean hasNext() {
        return messagePosition < msgs.length;
    }

    /**
     * {@inheritDoc}
     */
    public Object next() {
        try {
            Message m = msgs[messagePosition++];
            markMessageSeen(m);
            return m;
        } catch (MessagingException e) {
            close(false);
            throw new RuntimeException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void remove() {
        throw new UnsupportedOperationException();
    }

    /**
     * Closes a mail session
     *
     * @param checkSuccessful Check if it was a successful close
     */
    protected void close(boolean checkSuccessful) {
        try {
            closeStore(checkSuccessful, store, folder);
        } catch (MessagingException e) {
            throw new RuntimeException("Error closing mail store", e);
        }
    }

    /**
     * Get an instance of a mail folder
     *
     * @param mailActivationSpec The mail activation spec
     * @return The mail folder; <code>null</code> if not IMAP / POP based
     */
    public static MailFolder getInstance(MailActivationSpec mailActivationSpec) {
        if ("pop3".equals(mailActivationSpec.getStoreProtocol())) {
            return new POP3MailFolder(mailActivationSpec);
        } else if ("imap".equals(mailActivationSpec.getStoreProtocol())) {
            return new IMAPMailFolder(mailActivationSpec);
        } else if ("pop3s".equals(mailActivationSpec.getStoreProtocol())) {
            return new POP3sMailFolder(mailActivationSpec);
        } else if ("imaps".equals(mailActivationSpec.getStoreProtocol())) {
            return new IMAPsMailFolder(mailActivationSpec);
        }

        return null;
    }

    /**
     * Open a store
     *
     * @param session The mail session
     * @return The store
     * @throws NoSuchProviderException Thrown if there is no provider
     */
    protected abstract Store openStore(Session session) throws NoSuchProviderException;

    /**
     * Close a store
     *
     * @param success Check for successful close
     * @param store   The store
     * @param folder  The folder
     * @throws MessagingException Thrown if there is an error
     */
    protected abstract void closeStore(boolean success, Store store, Folder folder) throws MessagingException;

    /**
     * Get the messages from a folder
     *
     * @param folder The folder
     * @return The messages
     * @throws MessagingException Thrown if there is an error
     */
    protected abstract Message[] getMessages(Folder folder) throws MessagingException;

    /**
     * Mark a message as seen
     *
     * @param message The messages
     * @throws MessagingException Thrown if there is an error
     */
    protected abstract void markMessageSeen(Message message) throws MessagingException;
}
