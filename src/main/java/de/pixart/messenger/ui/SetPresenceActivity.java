package de.pixart.messenger.ui;

import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.os.Handler;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.TextView;

import java.util.List;

import de.pixart.messenger.R;
import de.pixart.messenger.databinding.ActivitySetPresenceBinding;
import de.pixart.messenger.entities.Account;
import de.pixart.messenger.entities.ListItem;
import de.pixart.messenger.entities.Presence;
import de.pixart.messenger.entities.PresenceTemplate;
import de.pixart.messenger.utils.UIHelper;

public class SetPresenceActivity extends XmppActivity implements View.OnClickListener {

    //data
    protected Account mAccount;
    private List<PresenceTemplate> mTemplates;

    private ActivitySetPresenceBinding binding;

    private Pair<Integer, Intent> mPostponedActivityResult;

    private Runnable onPresenceChanged = new Runnable() {
        @Override
        public void run() {
            finish();
        }
    };

    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_set_presence);
        ArrayAdapter adapter = ArrayAdapter.createFromResource(this,
                R.array.presence_show_options,
                R.layout.simple_list_item);
        this.binding.presenceShow.setAdapter(adapter);
        this.binding.presenceShow.setSelection(1);
        this.binding.changePresence.setOnClickListener(v -> executeChangePresence());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.change_presence, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == R.id.action_account_details) {
            if (mAccount != null) {
                switchToAccount(mAccount);
            }
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (xmppConnectionServiceBound && mAccount != null) {
                if (requestCode == REQUEST_ANNOUNCE_PGP) {
                    announcePgp(mAccount, null, data, onPresenceChanged);
                }
                this.mPostponedActivityResult = null;
            } else {
                this.mPostponedActivityResult = new Pair<>(requestCode, data);
            }
        }
    }

    private void executeChangePresence() {
        Presence.Status status = getStatusFromSpinner();
        boolean allAccounts = this.binding.allAccounts.isChecked();
        String statusMessage = this.binding.presenceStatusMessage.getText().toString().trim();
        if (allAccounts && noAccountUsesPgp()) {
            xmppConnectionService.changeStatus(status, statusMessage);
            finish();
        } else if (mAccount != null) {
            if (mAccount.getPgpId() == 0 || !hasPgp()) {
                xmppConnectionService.changeStatus(mAccount, status, statusMessage, true);
                finish();
            } else {
                xmppConnectionService.changeStatus(mAccount, status, statusMessage, false);
                announcePgp(mAccount, null, null, onPresenceChanged);
            }
        }
    }

    private Presence.Status getStatusFromSpinner() {
        switch (this.binding.presenceShow.getSelectedItemPosition()) {
            case 0:
                return Presence.Status.CHAT;
            case 2:
                return Presence.Status.AWAY;
            case 3:
                return Presence.Status.XA;
            case 4:
                return Presence.Status.DND;
            default:
                return Presence.Status.ONLINE;
        }
    }

    private void setStatusInSpinner(Presence.Status status) {
        switch (status) {
            case AWAY:
                this.binding.presenceShow.setSelection(2);
                break;
            case XA:
                this.binding.presenceShow.setSelection(3);
                break;
            case CHAT:
                this.binding.presenceShow.setSelection(0);
                break;
            case DND:
                this.binding.presenceShow.setSelection(4);
                break;
            default:
                this.binding.presenceShow.setSelection(1);
                break;
        }
    }

    @Override
    protected void refreshUiReal() {

    }

    @Override
    void onBackendConnected() {
        mAccount = extractAccount(getIntent());
        if (mAccount != null) {
            setStatusInSpinner(mAccount.getPresenceStatus());
            String message = mAccount.getPresenceStatusMessage();
            if (this.binding.presenceStatusMessage.getText().length() == 0 && message != null) {
                this.binding.presenceStatusMessage.append(message);
            }
            mTemplates = xmppConnectionService.getPresenceTemplates(mAccount);
            if (this.mPostponedActivityResult != null) {
                this.onActivityResult(mPostponedActivityResult.first, RESULT_OK, mPostponedActivityResult.second);
            }
            boolean e = noAccountUsesPgp();
            this.binding.allAccounts.setEnabled(e);
        }
        redrawTemplates();
    }

    private void redrawTemplates() {
        if (mTemplates == null || mTemplates.size() == 0) {
            this.binding.templates.setVisibility(View.GONE);
        } else {
            this.binding.templates.removeAllViews();
            this.binding.templates.setVisibility(View.VISIBLE);
            LayoutInflater inflater = getLayoutInflater();
            for (PresenceTemplate template : mTemplates) {
                View templateLayout = inflater.inflate(R.layout.presence_template, this.binding.templates, false);
                templateLayout.setTag(template);
                setListItemBackgroundOnView(templateLayout);
                templateLayout.setOnClickListener(this);
                TextView message = templateLayout.findViewById(R.id.presence_status_message);
                TextView status = templateLayout.findViewById(R.id.status);
                ImageButton button = templateLayout.findViewById(R.id.delete_button);
                button.setTag(template);
                button.setOnClickListener(this);
                ListItem.Tag tag = UIHelper.getTagForStatus(this, template.getStatus());
                status.setText(tag.getName());
                status.setBackgroundColor(tag.getColor());
                message.setText(template.getStatusMessage());
                this.binding.templates.addView(templateLayout);
            }
        }
    }

    @Override
    public void onClick(View v) {
        PresenceTemplate template = (PresenceTemplate) v.getTag();
        if (template == null) {
            return;
        }
        if (v.getId() == R.id.presence_template) {
            setStatusInSpinner(template.getStatus());
            this.binding.presenceStatusMessage.getEditableText().clear();
            this.binding.presenceStatusMessage.getEditableText().append(template.getStatusMessage());
            new Handler().post(() -> this.binding.scrollView.smoothScrollTo(0,0));
        } else if (v.getId() == R.id.delete_button) {
            xmppConnectionService.databaseBackend.deletePresenceTemplate(template);
            mTemplates.remove(template);
            redrawTemplates();
        }
    }
}
