package org.freenetproject.freemail;

import java.io.File;
import java.util.List;

public class MailPendingMessage extends MailMessage {

    private List<String> pendingRecipients;

    public MailPendingMessage(File f, int msg_seqnum) {
        super(f, msg_seqnum);
    }

    public void setPendingRecipients(List<String> pendingRecipients) {
        this.pendingRecipients = pendingRecipients;
    }

    public List<String> getPendingRecipients() {
        return pendingRecipients;
    }
}
