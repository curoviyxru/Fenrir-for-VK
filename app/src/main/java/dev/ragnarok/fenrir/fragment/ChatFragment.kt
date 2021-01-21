package dev.ragnarok.fenrir.fragment

import android.Manifest
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.app.Dialog
import android.content.*
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.SparseBooleanArray
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.yalantis.ucrop.UCrop
import dev.ragnarok.fenrir.*
import dev.ragnarok.fenrir.activity.*
import dev.ragnarok.fenrir.adapter.AttachmentsBottomSheetAdapter
import dev.ragnarok.fenrir.adapter.AttachmentsViewBinder
import dev.ragnarok.fenrir.adapter.MessagesAdapter
import dev.ragnarok.fenrir.api.model.VKApiMessage
import dev.ragnarok.fenrir.crypt.KeyLocationPolicy
import dev.ragnarok.fenrir.dialog.ImageSizeAlertDialog
import dev.ragnarok.fenrir.dialog.LandscapeExpandBottomSheetDialog
import dev.ragnarok.fenrir.fragment.base.PlaceSupportMvpFragment
import dev.ragnarok.fenrir.fragment.search.SearchContentType
import dev.ragnarok.fenrir.fragment.search.criteria.MessageSeachCriteria
import dev.ragnarok.fenrir.fragment.sheet.MessageAttachmentsFragment
import dev.ragnarok.fenrir.listener.BackPressCallback
import dev.ragnarok.fenrir.listener.EndlessRecyclerOnScrollListener
import dev.ragnarok.fenrir.listener.OnSectionResumeCallback
import dev.ragnarok.fenrir.listener.PicassoPauseOnScrollListener
import dev.ragnarok.fenrir.modalbottomsheetdialogfragment.ModalBottomSheetDialogFragment
import dev.ragnarok.fenrir.modalbottomsheetdialogfragment.Option
import dev.ragnarok.fenrir.modalbottomsheetdialogfragment.OptionRequest
import dev.ragnarok.fenrir.model.*
import dev.ragnarok.fenrir.model.Types
import dev.ragnarok.fenrir.model.selection.*
import dev.ragnarok.fenrir.mvp.core.IPresenterFactory
import dev.ragnarok.fenrir.mvp.presenter.ChatPresenter
import dev.ragnarok.fenrir.mvp.view.IChatView
import dev.ragnarok.fenrir.picasso.PicassoInstance
import dev.ragnarok.fenrir.picasso.transforms.RoundTransformation
import dev.ragnarok.fenrir.place.PlaceFactory
import dev.ragnarok.fenrir.settings.CurrentTheme
import dev.ragnarok.fenrir.settings.Settings
import dev.ragnarok.fenrir.upload.Upload
import dev.ragnarok.fenrir.upload.UploadDestination
import dev.ragnarok.fenrir.util.*
import dev.ragnarok.fenrir.util.Objects
import dev.ragnarok.fenrir.util.Utils.nonEmpty
import dev.ragnarok.fenrir.view.InputViewController
import dev.ragnarok.fenrir.view.LoadMoreFooterHelper
import dev.ragnarok.fenrir.view.WeakViewAnimatorAdapter
import dev.ragnarok.fenrir.view.emoji.BotKeyboardView
import dev.ragnarok.fenrir.view.emoji.EmojiconTextView
import dev.ragnarok.fenrir.view.emoji.EmojiconsPopup
import dev.ragnarok.fenrir.view.emoji.StickersKeyWordsAdapter
import java.io.File
import java.lang.ref.WeakReference
import java.util.*

class ChatFragment : PlaceSupportMvpFragment<ChatPresenter, IChatView>(), IChatView,
    InputViewController.OnInputActionCallback,
    BackPressCallback, MessagesAdapter.OnMessageActionListener,
    InputViewController.RecordActionsCallback,
    AttachmentsViewBinder.VoiceActionListener, EmojiconsPopup.OnStickerClickedListener,
    EmojiconsPopup.OnMyStickerClickedListener,
    EmojiconTextView.OnHashTagClickListener, BotKeyboardView.BotKeyboardViewDelegate {

    private var headerView: View? = null
    private var loadMoreFooterHelper: LoadMoreFooterHelper? = null

    private var recyclerView: RecyclerView? = null
    private var stickersKeywordsView: RecyclerView? = null
    private var stickersAdapter: StickersKeyWordsAdapter? = null
    private var adapter: MessagesAdapter? = null

    private var downMenuGroup: FrameLayout? = null

    private var inputViewController: InputViewController? = null
    private var emptyText: TextView? = null

    private var pinnedView: View? = null
    private var pinnedAvatar: ImageView? = null
    private var pinnedTitle: TextView? = null
    private var pinnedSubtitle: TextView? = null
    private var buttonUnpin: View? = null

    private val optionMenuSettings = SparseBooleanArray()

    private var toolbarRootView: ViewGroup? = null
    private var actionModeHolder: ActionModeHolder? = null

    private var editMessageGroup: ViewGroup? = null
    private var editMessageText: TextView? = null

    private var goto_button: FloatingActionButton? = null

    private var Writing_msg_Group: View? = null
    private var Writing_msg: TextView? = null
    private var Writing_msg_Ava: ImageView? = null
    private var Writing_msg_Type: ImageView? = null

    private var Title: TextView? = null
    private var SubTitle: TextView? = null

    private var Avatar: ImageView? = null

    private var toolbar: Toolbar? = null
    private var EmptyAvatar: TextView? = null

    private var InputView: View? = null

    private val requestRecordPermission = AppPerms.requestPermissions(
        this, arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    ) { presenter?.fireRecordPermissionsResolved() }
    private val requestCameraEditPermission = AppPerms.requestPermissions(
        this, arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE
        )
    ) { presenter?.fireEditCameraClick() }

    private val openCameraRequest = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { result ->
        if (result) {
            when (val defaultSize = Settings.get().main().uploadImageSize) {
                null -> {
                    ImageSizeAlertDialog.Builder(activity)
                        .setOnSelectedCallback { size -> presenter?.fireEditPhotoMaked(size) }
                        .show()
                }
                else -> presenter?.fireEditPhotoMaked(defaultSize)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_chat, container, false) as ViewGroup
        root.background = CurrentTheme.getChatBackground(activity)

        toolbar = root.findViewById(R.id.toolbar)
        toolbar?.inflateMenu(R.menu.menu_chat)
        PrepareOptionsMenu(toolbar?.menu!!)
        toolbar!!.setOnMenuItemClickListener { item: MenuItem ->
            OptionsItemSelected(item)
        }

        stickersKeywordsView = root.findViewById(R.id.stickers)
        stickersAdapter = StickersKeyWordsAdapter(requireActivity(), Collections.emptyList())
        stickersAdapter?.setStickerClickedListener { presenter?.fireStickerSendClick(it); presenter?.resetDraftMessage() }
        stickersKeywordsView?.layoutManager =
            LinearLayoutManager(requireActivity(), LinearLayoutManager.HORIZONTAL, false)
        stickersKeywordsView?.adapter = stickersAdapter
        stickersKeywordsView?.visibility = View.GONE

        downMenuGroup = root.findViewById(R.id.down_menu)

        Title = root.findViewById(R.id.dialog_title)
        SubTitle = root.findViewById(R.id.dialog_subtitle)
        Avatar = root.findViewById(R.id.toolbar_avatar)
        EmptyAvatar = root.findViewById(R.id.empty_avatar_text)

        emptyText = root.findViewById(R.id.fragment_chat_empty_text)
        toolbarRootView = root.findViewById(R.id.toolbar_root)

        Writing_msg_Group = root.findViewById(R.id.writingGroup)
        Writing_msg = root.findViewById(R.id.writing)
        Writing_msg_Ava = root.findViewById(R.id.writingava)
        Writing_msg_Type = root.findViewById(R.id.writing_type)
        InputView = root.findViewById(R.id.input_view)

        Writing_msg_Group?.visibility = View.GONE

        recyclerView = root.findViewById(R.id.fragment_friend_dialog_list)
        recyclerView?.apply {
            layoutManager = createLayoutManager()
            if (!Settings.get().main().is_smooth_chat) {
                itemAnimator?.changeDuration = 0
                itemAnimator?.addDuration = 0
                itemAnimator?.moveDuration = 0
                itemAnimator?.removeDuration = 0
            }
            addOnScrollListener(PicassoPauseOnScrollListener(Constants.PICASSO_TAG))
            addOnScrollListener(object : EndlessRecyclerOnScrollListener() {
                override fun onScrollToLastElement() {
                    presenter?.fireScrollToEnd()
                }
            })
        }

        ItemTouchHelper(MessagesReplyItemCallback {
            presenter?.fireResendSwipe(
                adapter!!.getItemRawPosition(
                    it
                )
            )
        }).attachToRecyclerView(recyclerView)

        headerView = inflater.inflate(R.layout.footer_load_more, recyclerView, false)

        loadMoreFooterHelper = LoadMoreFooterHelper.createFrom(headerView) {
            presenter?.fireLoadUpButtonClick()
        }

        inputViewController = InputViewController(requireActivity(), root, this)
            .also {
                it.setSendOnEnter(Settings.get().main().isSendByEnter)
                it.setRecordActionsCallback(this)
                it.setOnSickerClickListener(this)
                it.setOnMySickerClickListener(this)
                it.setKeyboardBotClickListener(this)
                it.setKeyboardBotLongClickListener {
                    run {
                        recyclerView?.scrollToPosition(0)
                        presenter?.reset_Hrono()
                        presenter?.fireRefreshClick()
                    }; true
                }
            }

        pinnedView = root.findViewById(R.id.pinned_root_view)
        pinnedAvatar = pinnedView?.findViewById(R.id.pinned_avatar)
        pinnedTitle = pinnedView?.findViewById(R.id.pinned_title)
        pinnedSubtitle = pinnedView?.findViewById(R.id.pinned_subtitle)
        buttonUnpin = pinnedView?.findViewById(R.id.buttonUnpin)
        buttonUnpin?.setOnClickListener { presenter?.fireUnpinClick() }

        pinnedView?.setOnLongClickListener {
            it.isVisible = false
            true
        }

        goto_button = root.findViewById(R.id.goto_button)
        goto_button?.let {
            if (Utils.isHiddenCurrent()) {
                it.setImageResource(R.drawable.attachment)
                it.setOnClickListener { presenter?.fireDialogAttachmentsClick() }
            } else {
                it.setImageResource(R.drawable.ic_outline_keyboard_arrow_up)
                it.setOnClickListener { recyclerView?.smoothScrollToPosition(presenter?.getConversation()!!.unreadCount) }
                it.setOnLongClickListener { presenter?.fireDialogAttachmentsClick(); true; }
            }

            if (!Settings.get().other().isEnable_last_read)
                it.visibility = View.GONE
            else
                it.visibility = View.VISIBLE
        }

        editMessageGroup = root.findViewById(R.id.editMessageGroup)
        editMessageText = editMessageGroup?.findViewById(R.id.editMessageText)
        editMessageGroup?.findViewById<View>(R.id.buttonCancelEditing)
            ?.setOnClickListener { presenter?.fireCancelEditingClick() }
        return root
    }

    override fun convert_to_keyboard(keyboard: Keyboard?) {
        inputViewController?.updateBotKeyboard(keyboard, !Utils.isHiddenCurrent())
    }

    override fun displayEditingMessage(message: Message?) {
        editMessageGroup?.visibility = if (message == null) View.GONE else View.VISIBLE
        message?.run {
            editMessageText?.text =
                if (body.isNullOrEmpty()) getString(R.string.attachments) else body
        }
    }

    override fun displayWriting(writeText: WriteText) {
        if (writeText.from_ids.size < 1) {
            return
        }
        Writing_msg_Group?.visibility = View.VISIBLE
        Writing_msg_Group?.alpha = 0.0f
        ObjectAnimator.ofFloat(Writing_msg_Group, View.ALPHA, 1f).setDuration(200).start()
        Writing_msg?.setText(if (writeText.isText) R.string.user_type_message else R.string.user_type_voice)
        Writing_msg_Type?.setImageResource(if (writeText.isText) R.drawable.pencil else R.drawable.voice)
        Writing_msg_Ava?.setImageResource(R.drawable.background_gray_round)
        presenter?.ResolveWritingInfo(requireActivity(), writeText)
    }

    @SuppressLint("SetTextI18n")
    override fun displayWriting(owner: Owner, count: Int, is_text: Boolean) {
        if (count > 1) {
            Writing_msg?.text = getString(R.string.many_users_typed, owner.fullName, count - 1)
        } else {
            Writing_msg?.text = owner.fullName
        }
        Writing_msg_Type?.setImageResource(if (is_text) R.drawable.pencil else R.drawable.voice)
        ViewUtils.displayAvatar(
            Writing_msg_Ava!!, CurrentTheme.createTransformationForAvatar(requireContext()),
            owner.get100photoOrSmaller(), null
        )
    }

    override fun updateStickers(items: List<Sticker>) {
        if (Utils.isEmpty(items)) {
            stickersKeywordsView?.visibility = View.GONE
        } else {
            stickersKeywordsView?.visibility = View.VISIBLE
        }
        stickersAdapter?.setData(items)
    }

    override fun hideWriting() {
        val animator: ObjectAnimator? = ObjectAnimator.ofFloat(Writing_msg_Group, View.ALPHA, 0.0f)
        animator?.addListener(object : WeakViewAnimatorAdapter<View>(Writing_msg_Group) {
            override fun onAnimationEnd(view: View) {
                Writing_msg_Group?.visibility = View.GONE
            }

            override fun onAnimationStart(view: View) {
            }

            override fun onAnimationCancel(view: View) {
            }
        })
        animator?.setDuration(200)?.start()
    }

    private class ActionModeHolder(val rootView: View, fragment: ChatFragment) :
        View.OnClickListener {

        override fun onClick(v: View) {
            when (v.id) {
                R.id.buttonClose -> hide()
                R.id.buttonEdit -> {
                    reference.get()?.presenter?.fireActionModeEditClick()
                    hide()
                }
                R.id.buttonForward -> {
                    reference.get()?.presenter?.onActionModeForwardClick()
                    hide()
                }
                R.id.buttonCopy -> {
                    reference.get()?.presenter?.fireActionModeCopyClick()
                    hide()
                }
                R.id.buttonDelete -> {
                    reference.get()?.presenter?.fireActionModeDeleteClick()
                    hide()
                }
                R.id.buttonPin -> {
                    reference.get()?.presenter?.fireActionModePinClick()
                    hide()
                }
                R.id.buttonStar -> {
                    reference.get()?.presenter?.fireActionModeStarClick()
                    hide()
                }
            }
        }

        val reference = WeakReference(fragment)
        val buttonClose: View = rootView.findViewById(R.id.buttonClose)
        val buttonEdit: View = rootView.findViewById(R.id.buttonEdit)
        val buttonForward: View = rootView.findViewById(R.id.buttonForward)
        val buttonCopy: View = rootView.findViewById(R.id.buttonCopy)
        val buttonDelete: View = rootView.findViewById(R.id.buttonDelete)
        val buttonPin: View = rootView.findViewById(R.id.buttonPin)
        val buttonStar: ImageView = rootView.findViewById(R.id.buttonStar)
        val titleView: TextView = rootView.findViewById(R.id.actionModeTitle)

        init {
            buttonClose.setOnClickListener(this)
            buttonEdit.setOnClickListener(this)
            buttonForward.setOnClickListener(this)
            buttonCopy.setOnClickListener(this)
            buttonDelete.setOnClickListener(this)
            buttonPin.setOnClickListener(this)
            buttonStar.setOnClickListener(this)
        }

        fun show() {
            rootView.visibility = View.VISIBLE
        }

        fun isVisible(): Boolean = rootView.visibility == View.VISIBLE

        fun hide() {
            if (Settings.get().main().isMessages_menu_down) {
                reference.get()?.InputView?.visibility = View.VISIBLE
                reference.get()?.downMenuGroup?.visibility = View.GONE
            }
            rootView.visibility = View.GONE
            reference.get()?.presenter?.fireActionModeDestroy()
        }
    }

    override fun onRecordCancel() {
        presenter?.fireRecordCancelClick()
    }

    override fun onSwithToRecordMode() {
        presenter?.fireRecordingButtonClick()
    }

    override fun onRecordSendClick() {
        presenter?.fireRecordSendClick()
    }

    override fun onResumePauseClick() {
        presenter?.fireRecordResumePauseClick()
    }

    private fun createLayoutManager(): RecyclerView.LayoutManager {
        return LinearLayoutManager(activity, RecyclerView.VERTICAL, true)
    }

    override fun displayMessages(messages: List<Message>, lastReadId: LastReadId) {
        adapter = MessagesAdapter(activity, messages, lastReadId, this, false)
            .also {
                it.setOnMessageActionListener(this)
                it.setVoiceActionListener(this)
                it.addFooter(headerView)
                it.setOnHashTagClickListener(this)
            }

        recyclerView?.adapter = adapter
    }

    override fun notifyMessagesUpAdded(position: Int, count: Int) {
        adapter?.run {
            notifyItemRangeChanged(position + headersCount, count) //+header if exist
        }
    }

    override fun notifyDataChanged() {
        adapter?.notifyDataSetChanged()
    }

    override fun notifyMessagesDownAdded(count: Int) {

    }

    override fun configNowVoiceMessagePlaying(
        voiceId: Int,
        progress: Float,
        paused: Boolean,
        amin: Boolean
    ) {
        adapter?.configNowVoiceMessagePlaying(voiceId, progress, paused, amin)
    }

    override fun bindVoiceHolderById(
        holderId: Int,
        play: Boolean,
        paused: Boolean,
        progress: Float,
        amin: Boolean
    ) {
        adapter?.bindVoiceHolderById(holderId, play, paused, progress, amin)
    }

    override fun disableVoicePlaying() {
        adapter?.disableVoiceMessagePlaying()
    }

    override fun getPresenterFactory(saveInstanceState: Bundle?): IPresenterFactory<ChatPresenter> =
        object : IPresenterFactory<ChatPresenter> {
            override fun create(): ChatPresenter {
                val aid = requireArguments().getInt(Extra.ACCOUNT_ID)
                val messagesOwnerId = requireArguments().getInt(Extra.OWNER_ID)
                val peer = requireArguments().getParcelable<Peer>(Extra.PEER)
                return ChatPresenter(
                    aid,
                    messagesOwnerId,
                    peer!!,
                    createStartConfig(),
                    saveInstanceState
                )
            }
        }

    private fun createStartConfig(): ChatConfig {
        val config = ChatConfig()

        config.isCloseOnSend = requireActivity() is SendAttachmentsActivity

        val inputStreams = ActivityUtils.checkLocalStreams(requireActivity())
        config.uploadFiles = if (inputStreams == null) null else {
            if (inputStreams.uris.nullOrEmpty()) null else inputStreams.uris
        }
        config.uploadFilesMimeType = inputStreams?.mime

        val models =
            requireActivity().intent.getParcelableExtra<ModelsBundle>(MainActivity.EXTRA_INPUT_ATTACHMENTS)

        models?.run {
            config.appendAll(this)
        }

        val initialText = ActivityUtils.checkLinks(requireActivity())
        config.initialText = if (initialText.isNullOrEmpty()) null else initialText
        return config
    }

    override fun setupLoadUpHeaderState(@LoadMoreState state: Int) {
        loadMoreFooterHelper?.switchToState(state)
    }

    override fun displayDraftMessageAttachmentsCount(count: Int) {
        inputViewController?.setAttachmentsCount(count)
    }

    override fun displayDraftMessageText(text: String?) {
        inputViewController?.setTextQuietly(text)
        presenter?.fireTextEdited(text)
    }

    override fun AppendMessageText(text: String?) {
        inputViewController?.AppendTextQuietly(text)
    }

    override fun displayToolbarTitle(text: String?) {
        Title?.text = text
    }

    override fun displayToolbarSubtitle(text: String?) {
        SubTitle?.text = text
    }

    override fun displayToolbarAvatar(peer: Peer?) {
        if (nonEmpty(peer?.avaUrl)) {
            EmptyAvatar?.visibility = View.GONE
            PicassoInstance.with()
                .load(peer?.avaUrl)
                .transform(RoundTransformation())
                .into(Avatar!!)
        } else {
            PicassoInstance.with().cancelRequest(Avatar!!)
            EmptyAvatar?.visibility = View.VISIBLE
            var name: String = peer!!.title
            if (name.length > 2) name = name.substring(0, 2)
            name = name.trim { it <= ' ' }
            EmptyAvatar?.text = name
            Avatar?.setImageBitmap(
                RoundTransformation().transform(
                    Utils.createGradientChatImage(
                        200,
                        200,
                        peer.id
                    )
                )
            )
        }
    }

    override fun setupPrimaryButtonAsEditing(canSave: Boolean) {
        inputViewController?.switchModeToEditing(canSave)
    }

    override fun setupPrimaryButtonAsRecording() {
        inputViewController?.switchModeToRecording()
    }

    override fun setupPrimaryButtonAsRegular(canSend: Boolean, canStartRecoring: Boolean) {
        inputViewController?.switchModeToNormal(canSend, canStartRecoring)
    }

    override fun requestRecordPermissions() {
        requestRecordPermission.launch()
    }

    override fun displayRecordingDuration(time: Long) {
        inputViewController?.setRecordingDuration(time)
    }

    override fun doCloseAfterSend() {
        try {
            requireActivity().finish()
        } catch (ignored: Exception) {

        }
    }

    override fun goToMessagesLookup(accountId: Int, peerId: Int, messageId: Int) {
        PlaceFactory.getMessagesLookupPlace(accountId, peerId, messageId)
            .tryOpenWith(requireActivity())
    }

    override fun displayPinnedMessage(pinned: Message?, canChange: Boolean) {
        pinnedView?.run {
            isVisible = pinned != null
            pinned?.run {
                ViewUtils.displayAvatar(
                    pinnedAvatar!!, CurrentTheme.createTransformationForAvatar(requireContext()),
                    sender.get100photoOrSmaller(), null
                )

                pinnedTitle?.text = sender.fullName
                if (Utils.isEmpty(body) && pinned.isHasAttachments) {
                    body = getString(R.string.attachments)
                }
                pinnedSubtitle?.text = body
                buttonUnpin?.visibility = if (canChange) View.VISIBLE else View.GONE
                pinnedView?.setOnClickListener { presenter?.fireMessagesLookup(pinned); }
            }
        }
    }

    override fun hideInputView() {
        inputViewController?.run {
            switchModeToDisabled()
        }
    }

    override fun onVoiceHolderBinded(voiceMessageId: Int, voiceHolderId: Int) {
        presenter?.fireVoiceHolderCreated(voiceMessageId, voiceHolderId)
    }

    override fun onVoicePlayButtonClick(
        voiceHolderId: Int,
        voiceMessageId: Int,
        voiceMessage: VoiceMessage
    ) {
        presenter?.fireVoicePlayButtonClick(voiceHolderId, voiceMessageId, voiceMessage)
    }

    override fun onTranscript(voiceMessageId: String, messageId: Int) {
        presenter?.fireTranscript(voiceMessageId, messageId)
    }

    override fun onStickerClick(sticker: Sticker) {
        presenter?.fireStickerSendClick(sticker)
    }

    override fun onHashTagClicked(hashTag: String) {
        presenter?.fireHashtagClick(hashTag)
    }

    override fun showActionMode(
        title: String,
        canEdit: Boolean,
        canPin: Boolean,
        canStar: Boolean,
        doStar: Boolean
    ) {
        if (!Settings.get().main().isMessages_menu_down) {
            toolbarRootView?.run {
                if (childCount == 1) {
                    val v =
                        LayoutInflater.from(context).inflate(R.layout.view_action_mode, this, false)
                    actionModeHolder = ActionModeHolder(v, this@ChatFragment)
                    addView(v)
//              bringChildToFront(v)
                }
            }
        } else {
            inputViewController?.closeBotKeyboard()
            inputViewController?.showEmoji(false)
            InputView?.visibility = View.INVISIBLE
            downMenuGroup?.run {
                visibility = View.VISIBLE
                if (childCount == 0) {
                    val v =
                        LayoutInflater.from(context).inflate(R.layout.view_action_mode, this, false)
                    actionModeHolder = ActionModeHolder(v, this@ChatFragment)
                    addView(v)
//              bringChildToFront(v)
                }
            }
        }

        actionModeHolder?.run {
            show()
            titleView.text = title
            buttonEdit.visibility = if (canEdit) View.VISIBLE else View.GONE
            buttonPin.visibility = if (canPin) View.VISIBLE else View.GONE
            buttonStar.visibility = if (canStar) View.VISIBLE else View.GONE
            buttonStar.setImageResource(if (doStar) R.drawable.star_add else R.drawable.star_none)
        }
    }

    override fun finishActionMode() {
        actionModeHolder?.rootView?.visibility = View.GONE
        if (Settings.get().main().isMessages_menu_down) {
            InputView?.visibility = View.VISIBLE
            downMenuGroup?.visibility = View.GONE
        }
    }

    private val openRequestPhotoResize =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.let {
                    presenter?.fireFilePhotoForUploadSelected(
                        UCrop.getOutput(it)!!.path,
                        Upload.IMAGE_SIZE_FULL
                    )
                }
            } else if (result.resultCode == UCrop.RESULT_ERROR) {
                result.data?.let { showThrowable(UCrop.getError(it)) }
            }
        }

    private fun onEditLocalPhotosSelected(photos: List<LocalPhoto>) {
        when (val defaultSize = Settings.get().main().uploadImageSize) {
            null -> {
                ImageSizeAlertDialog.Builder(activity)
                    .setOnSelectedCallback { size ->
                        presenter?.fireEditLocalPhotosSelected(
                            photos,
                            size
                        )
                    }
                    .show()
            }
            Upload.IMAGE_SIZE_CROPPING -> {
                if (photos.size == 1) {
                    var to_up = photos[0].fullImageUri
                    if (File(to_up.path!!).isFile) {
                        to_up = Uri.fromFile(File(to_up.path!!))
                    }

                    openRequestPhotoResize.launch(
                        UCrop.of(
                            to_up,
                            Uri.fromFile(File(requireActivity().externalCacheDir.toString() + File.separator + "scale.jpg"))
                        )
                            .getIntent(requireActivity())
                    )
                } else {
                    presenter?.fireEditLocalPhotosSelected(photos, Upload.IMAGE_SIZE_FULL)
                }
            }
            else -> presenter?.fireEditLocalPhotosSelected(photos, defaultSize)
        }
    }

    private fun onEditLocalVideoSelected(video: LocalVideo) {
        presenter?.fireEditLocalVideoSelected(video)
    }

    private fun onEditLocalFileSelected(file: String) {
        MaterialAlertDialogBuilder(requireActivity())
            .setTitle(R.string.select)
            .setNegativeButton(R.string.video) { _, _ ->
                run {
                    presenter?.fireFileVideoForUploadSelected(file)
                }
            }
            .setPositiveButton(R.string.photo) { _, _ ->
                run {
                    when (val defaultSize = Settings.get().main().uploadImageSize) {
                        null -> {
                            ImageSizeAlertDialog.Builder(activity)
                                .setOnSelectedCallback { size ->
                                    presenter?.fireFilePhotoForUploadSelected(
                                        file,
                                        size
                                    )
                                }
                                .show()
                        }
                        Upload.IMAGE_SIZE_CROPPING -> {
                            openRequestPhotoResize.launch(
                                UCrop.of(
                                    Uri.fromFile(File(file)),
                                    Uri.fromFile(File(requireActivity().externalCacheDir.toString() + File.separator + "scale.jpg"))
                                )
                                    .getIntent(requireActivity())
                            )
                        }
                        else -> presenter?.fireFilePhotoForUploadSelected(file, defaultSize)
                    }
                }
            }
            .create().show()
    }

    override fun goToMessageAttachmentsEditor(
        accountId: Int, messageOwnerId: Int, destination: UploadDestination,
        body: String?, attachments: ModelsBundle?
    ) {
        val fragment = MessageAttachmentsFragment.newInstance(
            accountId,
            messageOwnerId,
            destination.id,
            attachments
        )
        parentFragmentManager.setFragmentResultListener(
            MessageAttachmentsFragment.MESSAGE_CLOSE_ONLY,
            fragment,
            { _, _ -> presenter?.fireSendClickFromAttachmens() })
        parentFragmentManager.setFragmentResultListener(
            MessageAttachmentsFragment.MESSAGE_SYNC_ATTACHMENTS,
            fragment,
            { _, bundle ->
                run {
                    if (bundle.containsKey(Extra.BUNDLE)) {
                        val modelBundle = bundle.getParcelable<ModelsBundle>(Extra.BUNDLE)
                        if (modelBundle != null)
                            presenter?.fireEditMessageResult(modelBundle)
                    }
                }
            })
        fragment.show(parentFragmentManager, "message-attachments")
    }

    private val openRequestPhoto =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.data != null && result.resultCode == RESULT_OK) {
                val vkphotos: List<Photo> =
                    result.data?.getParcelableArrayListExtra(Extra.ATTACHMENTS)
                        ?: Collections.emptyList()
                val localPhotos: List<LocalPhoto> =
                    result.data?.getParcelableArrayListExtra(Extra.PHOTOS)
                        ?: Collections.emptyList()

                val vid: LocalVideo? = result.data?.getParcelableExtra(Extra.VIDEO)

                val file = result.data?.getStringExtra(FileManagerFragment.returnFileParameter)

                if (file != null && nonEmpty(file)) {
                    onEditLocalFileSelected(file)
                } else if (vkphotos.isNotEmpty()) {
                    presenter?.fireEditAttachmentsSelected(vkphotos)
                } else if (localPhotos.isNotEmpty()) {
                    onEditLocalPhotosSelected(localPhotos)
                } else if (vid != null) {
                    onEditLocalVideoSelected(vid)
                }
            }
        }

    override fun startImagesSelection(accountId: Int, ownerId: Int) {
        val sources = Sources()
            .with(LocalPhotosSelectableSource())
            .with(LocalGallerySelectableSource())
            .with(LocalVideosSelectableSource())
            .with(VkPhotosSelectableSource(accountId, ownerId))
            .with(FileManagerSelectableSource())

        val intent = DualTabPhotoActivity.createIntent(activity, 10, sources)
        openRequestPhoto.launch(intent)
    }

    private val openRequestAudioVideoDoc =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.data != null && result.resultCode == RESULT_OK) {
                val attachments: List<AbsModel> =
                    result.data?.getParcelableArrayListExtra(Extra.ATTACHMENTS)
                        ?: Collections.emptyList()
                presenter?.fireEditAttachmentsSelected(attachments)
            }
        }

    override fun startVideoSelection(accountId: Int, ownerId: Int) {
        val intent = VideoSelectActivity.createIntent(activity, accountId, ownerId)
        openRequestAudioVideoDoc.launch(intent)
    }

    override fun startAudioSelection(accountId: Int) {
        val intent = AudioSelectActivity.createIntent(activity, accountId)
        openRequestAudioVideoDoc.launch(intent)
    }

    override fun startDocSelection(accountId: Int) {
        val intent = AttachmentsActivity.createIntent(activity, accountId, Types.DOC)
        openRequestAudioVideoDoc.launch(intent)
    }

    private fun onEditCameraClick() {
        if (AppPerms.hasCameraPermission(requireContext())) {
            presenter?.fireEditCameraClick()
        } else {
            requestCameraEditPermission.launch()
        }
    }

    override fun startCamera(fileUri: Uri) {
        openCameraRequest.launch(fileUri)
    }

    override fun showDeleteForAllDialog(ids: ArrayList<Message>) {
        MaterialAlertDialogBuilder(requireActivity()).apply {
            setTitle(R.string.confirmation)
            setMessage(R.string.messages_delete_for_all_question_message)
            setCancelable(true)
            setNeutralButton(R.string.button_super_delete) { _, _ -> presenter?.fireDeleteSuper(ids) }
            setPositiveButton(R.string.button_for_all) { _, _ ->
                presenter?.fireDeleteForAllClick(
                    ids
                )
            }
            setNegativeButton(R.string.button_for_me) { _, _ -> presenter?.fireDeleteForMeClick(ids) }
        }.show()
    }

    @SuppressLint("ShowToast")
    override fun showSnackbar(@StringRes res: Int, isLong: Boolean) {
        val view = super.getView()
        if (Objects.nonNull(view)) {
            Snackbar.make(view!!, res, if (isLong) Snackbar.LENGTH_LONG else Snackbar.LENGTH_SHORT)
                .setAnchorView(InputView).show()
        }
    }

    private class EditAttachmentsHolder(
        rootView: View,
        fragment: ChatFragment,
        attachments: MutableList<AttachmenEntry>
    ) : AttachmentsBottomSheetAdapter.ActionListener, View.OnClickListener {
        override fun onClick(v: View) {
            when (v.id) {
                R.id.buttonHide -> reference.get()?.hideEditAttachmentsDialog()
                R.id.buttonSave -> reference.get()?.onEditAttachmentSaveClick()
                R.id.buttonVideo -> reference.get()?.presenter?.onEditAddVideoClick()
                R.id.buttonAudio -> reference.get()?.presenter?.onEditAddAudioClick()
                R.id.buttonDoc -> reference.get()?.presenter?.onEditAddDocClick()
                R.id.buttonCamera -> reference.get()?.onEditCameraClick()
            }
        }

        override fun onAddPhotoButtonClick() {
            reference.get()?.presenter?.fireEditAddImageClick()
        }

        override fun onButtonRemoveClick(entry: AttachmenEntry) {
            reference.get()?.presenter?.fireEditAttachmentRemoved(entry)
        }

        override fun onButtonRetryClick(entry: AttachmenEntry) {
            reference.get()?.presenter?.fireEditAttachmentRetry(entry)
        }

        val reference = WeakReference(fragment)
        val recyclerView: RecyclerView = rootView.findViewById(R.id.recyclerView)
        val emptyView: View = rootView.findViewById(R.id.no_attachments_text)
        val adapter = AttachmentsBottomSheetAdapter(rootView.context, attachments, this)

        init {
            recyclerView.layoutManager =
                LinearLayoutManager(rootView.context, LinearLayoutManager.HORIZONTAL, false)
            recyclerView.adapter = adapter

            rootView.findViewById<View>(R.id.buttonHide).setOnClickListener(this)
            rootView.findViewById<View>(R.id.buttonVideo).setOnClickListener(this)
            rootView.findViewById<View>(R.id.buttonAudio).setOnClickListener(this)
            rootView.findViewById<View>(R.id.buttonDoc).setOnClickListener(this)
            rootView.findViewById<View>(R.id.buttonCamera).setOnClickListener(this)
            rootView.findViewById<View>(R.id.buttonSave).setOnClickListener(this)

            checkEmptyViewVisibility()
        }

        fun checkEmptyViewVisibility() {
            emptyView.visibility = if (adapter.itemCount < 2) View.VISIBLE else View.INVISIBLE
        }

        fun notifyAttachmentRemoved(index: Int) {
            adapter.notifyItemRemoved(index + 1)
            checkEmptyViewVisibility()
        }

        fun notifyAttachmentChanged(index: Int) {
            adapter.notifyItemChanged(index + 1)
        }

        fun notifyAttachmentsAdded(position: Int, count: Int) {
            adapter.notifyItemRangeInserted(position + 1, count)
            checkEmptyViewVisibility()
        }

        fun notifyAttachmentProgressUpdate(index: Int, progress: Int) {
            adapter.changeUploadProgress(index, progress, true)
        }
    }

    override fun notifyEditUploadProgressUpdate(index: Int, progress: Int) {
        editAttachmentsHolder?.notifyAttachmentProgressUpdate(index, progress)
    }

    override fun notifyEditAttachmentsAdded(position: Int, size: Int) {
        editAttachmentsHolder?.notifyAttachmentsAdded(position, size)
    }

    override fun notifyEditAttachmentChanged(index: Int) {
        editAttachmentsHolder?.notifyAttachmentChanged(index)
    }

    override fun notifyEditAttachmentRemoved(index: Int) {
        editAttachmentsHolder?.notifyAttachmentRemoved(index)
    }

    private fun onEditAttachmentSaveClick() {
        hideEditAttachmentsDialog()
        presenter?.fireEditMessageSaveClick()
    }

    private fun hideEditAttachmentsDialog() {
        editAttachmentsDialog?.dismiss()
    }

    override fun onSaveClick() {
        presenter?.fireEditMessageSaveClick()
    }

    private var editAttachmentsHolder: EditAttachmentsHolder? = null
    private var editAttachmentsDialog: Dialog? = null

    override fun showEditAttachmentsDialog(attachments: MutableList<AttachmenEntry>) {
        val view = View.inflate(requireActivity(), R.layout.bottom_sheet_attachments_edit, null)

        val reference = WeakReference(this)
        editAttachmentsHolder = EditAttachmentsHolder(view, this, attachments)
        editAttachmentsDialog = LandscapeExpandBottomSheetDialog(requireActivity())
            .apply {
                setContentView(view)
                setOnDismissListener { reference.get()?.editAttachmentsHolder = null }
                show()
            }
    }

    override fun showErrorSendDialog(message: Message) {
        val items = arrayOf(getString(R.string.try_again), getString(R.string.delete))

        MaterialAlertDialogBuilder(requireActivity())
            .setTitle(R.string.sending_message_failed)
            .setItems(items) { _, i ->
                when (i) {
                    0 -> presenter?.fireSendAgainClick(message)
                    1 -> presenter?.fireErrorMessageDeleteClick(message)
                }
            }
            .setCancelable(true)
            .show()
    }

    override fun notifyItemRemoved(position: Int) {
        adapter?.run {
            notifyItemRemoved(position + headersCount) // +headers count
        }
    }

    override fun configOptionMenu(
        canLeaveChat: Boolean,
        canChangeTitle: Boolean,
        canShowMembers: Boolean,
        encryptionStatusVisible: Boolean,
        encryprionEnabled: Boolean,
        encryptionPlusEnabled: Boolean,
        keyExchangeVisible: Boolean,
        HronoVisible: Boolean,
        ProfileVisible: Boolean,
        InviteLink: Boolean
    ) {
        optionMenuSettings.apply {
            put(LEAVE_CHAT_VISIBLE, canLeaveChat)
            put(CHANGE_CHAT_TITLE_VISIBLE, canChangeTitle)
            put(CHAT_MEMBERS_VISIBLE, canShowMembers)
            put(ENCRYPTION_STATUS_VISIBLE, encryptionStatusVisible)
            put(ENCRYPTION_ENABLED, encryprionEnabled)
            put(ENCRYPTION_PLUS_ENABLED, encryptionPlusEnabled)
            put(KEY_EXCHANGE_VISIBLE, keyExchangeVisible)
            put(HRONO_VISIBLE, HronoVisible)
            put(PROFILE_VISIBLE, ProfileVisible)
            put(CAN_GENERATE_INVITE_LINK, InviteLink)
        }

        try {
            PrepareOptionsMenu(toolbar?.menu!!)
        } catch (ignored: Exception) {

        }

    }

    override fun goToSearchMessage(accountId: Int, peer: Peer) {
        val criteria = MessageSeachCriteria("").setPeerId(peer.id)
        PlaceFactory.getSingleTabSearchPlace(accountId, SearchContentType.MESSAGES, criteria)
            .tryOpenWith(requireActivity())
    }

    private val openRequestUploadChatAvatar =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.let { presenter?.fireNewChatPhotoSelected(UCrop.getOutput(it)!!.path!!) }
            } else if (result.resultCode == UCrop.RESULT_ERROR) {
                result.data?.let { showThrowable(UCrop.getError(it)) }
            }
        }

    override fun showImageSizeSelectDialog(streams: List<Uri>) {
        ImageSizeAlertDialog.Builder(activity)
            .setOnSelectedCallback { size ->
                run {
                    if (size == Upload.IMAGE_SIZE_CROPPING && streams.size == 1) {
                        var to_up = streams[0]
                        if (File(to_up.path!!).isFile) {
                            to_up = Uri.fromFile(File(to_up.path!!))
                        }

                        openRequestPhotoResize.launch(
                            UCrop.of(
                                to_up,
                                Uri.fromFile(File(requireActivity().externalCacheDir.toString() + File.separator + "scale.jpg"))
                            )
                                .getIntent(requireActivity())
                        )
                    } else {
                        presenter?.fireImageUploadSizeSelected(streams, size)
                    }
                }
            }
            .setOnCancelCallback { presenter?.fireUploadCancelClick() }
            .show()
    }

    override fun resetUploadImages() {
        ActivityUtils.resetInputPhotos(requireActivity())
    }

    override fun resetInputAttachments() {
        requireActivity().intent.removeExtra(MainActivity.EXTRA_INPUT_ATTACHMENTS)
        ActivityUtils.resetInputText(requireActivity())
    }

    private fun resolveToolbarNavigationIcon() {
        if (Objects.isNull(toolbar)) return
        val tr = AppCompatResources.getDrawable(requireActivity(), R.drawable.arrow_left)
        Utils.setColorFilter(tr, CurrentTheme.getColorPrimary(requireActivity()))
        toolbar!!.navigationIcon = tr
        toolbar!!.setNavigationOnClickListener { requireActivity().onBackPressed() }
    }

    private fun resolveLeftButton(peerId: Int) {
        try {
            if (Objects.nonNull(toolbar)) {
                resolveToolbarNavigationIcon()
                if (peerId < VKApiMessage.CHAT_PEER) {
                    Avatar?.setOnClickListener {
                        showUserWall(Settings.get().accounts().current, peerId)
                    }
                    Avatar?.setOnLongClickListener {
                        presenter?.fireLongAvatarClick(peerId)
                        true
                    }
                } else {
                    Avatar?.setOnClickListener {
                        presenter?.fireShowChatMembers()
                    }
                    Avatar?.setOnLongClickListener {
                        AppendMessageText("@all,")
                        true
                    }
                }
            }
        } catch (ignored: java.lang.Exception) {
        }
    }

    private fun insertDomain(owner: Owner) {
        if (nonEmpty(owner.domain)) {
            AppendMessageText("@" + owner.domain + ",")
        } else {
            AppendMessageText("@id" + owner.ownerId + ",")
        }
    }

    override fun notifyChatResume(accountId: Int, peerId: Int, title: String?, image: String?) {
        if (activity is OnSectionResumeCallback) {
            (activity as OnSectionResumeCallback).onChatResume(accountId, peerId, title, image)
        }
        Title?.text = title
        resolveLeftButton(peerId)
    }

    override fun goToConversationAttachments(accountId: Int, peerId: Int) {
        val types = arrayOf(
            FindAttachmentType.TYPE_PHOTO,
            FindAttachmentType.TYPE_VIDEO,
            FindAttachmentType.TYPE_DOC,
            FindAttachmentType.TYPE_AUDIO,
            FindAttachmentType.TYPE_LINK,
            FindAttachmentType.TYPE_POST
        )

        val menus = ModalBottomSheetDialogFragment.Builder().apply {
            add(OptionRequest(0, getString(R.string.photos), R.drawable.photo_album))
            add(OptionRequest(1, getString(R.string.videos), R.drawable.video))
            add(OptionRequest(2, getString(R.string.documents), R.drawable.book))
            add(OptionRequest(3, getString(R.string.music), R.drawable.song))
            add(OptionRequest(4, getString(R.string.links), R.drawable.web))
            add(OptionRequest(5, getString(R.string.posts), R.drawable.pencil))
        }

        menus.show(childFragmentManager, "attachments_select",
            object : ModalBottomSheetDialogFragment.Listener {
                override fun onModalOptionSelected(option: Option) {
                    showConversationAttachments(accountId, peerId, types[option.id])
                }
            })
    }

    private fun showConversationAttachments(accountId: Int, peerId: Int, type: String) {
        PlaceFactory.getConversationAttachmentsPlace(accountId, peerId, type)
            .tryOpenWith(requireActivity())
    }

    override fun goToChatMembers(accountId: Int, chatId: Int) {
        PlaceFactory.getChatMembersPlace(accountId, chatId).tryOpenWith(requireActivity())
    }

    override fun showChatMembers(accountId: Int, chatId: Int) {
        ChatUsersDomainFragment.newInstance(
            Settings.get().accounts().current,
            chatId
        ) { t -> insertDomain(t) }.show(childFragmentManager, "chat_users_domain")
    }

    private val openRequestSelectPhotoToChatAvatar =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.data != null && result.resultCode == RESULT_OK) {
                val photos: ArrayList<LocalPhoto>? =
                    result.data?.getParcelableArrayListExtra(Extra.PHOTOS)
                if (nonEmpty(photos)) {
                    var to_up = photos!![0].fullImageUri
                    if (File(to_up.path!!).isFile) {
                        to_up = Uri.fromFile(File(to_up.path!!))
                    }
                    openRequestUploadChatAvatar.launch(
                        UCrop.of(
                            to_up,
                            Uri.fromFile(File(requireActivity().externalCacheDir.toString() + File.separator + "scale.jpg"))
                        )
                            .withAspectRatio(1f, 1f)
                            .getIntent(requireActivity())
                    )
                }
            }
        }

    override fun showChatTitleChangeDialog(initialValue: String?) {
        MaterialAlertDialogBuilder(requireActivity())
            .setTitle(R.string.edit_chat)
            .setCancelable(true)
            .setPositiveButton(R.string.change_name) { _, _ ->
                run {
                    InputTextDialog.Builder(requireActivity())
                        .setAllowEmpty(false)
                        .setInputType(InputType.TYPE_CLASS_TEXT)
                        .setValue(initialValue)
                        .setTitleRes(R.string.change_chat_title)
                        .setCallback { newValue -> presenter?.fireChatTitleTyped(newValue) }
                        .show()
                }
            }
            .setNeutralButton(R.string.change_photo) { _, _ ->
                val attachPhotoIntent = Intent(requireActivity(), PhotosActivity::class.java)
                attachPhotoIntent.putExtra(PhotosActivity.EXTRA_MAX_SELECTION_COUNT, 1)
                openRequestSelectPhotoToChatAvatar.launch(attachPhotoIntent)
            }
            .setNegativeButton(R.string.delete_photo) { _, _ -> presenter?.fireDeleteChatPhoto() }
            .show()
    }

    override fun showUserWall(accountId: Int, peerId: Int) {
        PlaceFactory.getOwnerWallPlace(accountId, peerId, null).tryOpenWith(requireActivity())
    }

    override fun forwardMessagesToAnotherConversation(
        messages: ArrayList<Message>,
        accountId: Int
    ) {
        SendAttachmentsActivity.startForSendAttachments(
            requireActivity(),
            accountId,
            FwdMessages(messages)
        )
    }

    override fun diplayForwardTypeSelectDialog(messages: ArrayList<Message>) {
        val items = arrayOf(getString(R.string.here), getString(R.string.to_another_dialogue))

        val listener = DialogInterface.OnClickListener { _, which ->
            when (which) {
                0 -> presenter?.fireForwardToHereClick(messages)
                1 -> presenter?.fireForwardToAnotherClick(messages)
            }
        }

        MaterialAlertDialogBuilder(requireActivity())
            .setItems(items, listener)
            .setCancelable(true)
            .show()
    }

    override fun setEmptyTextVisible(visible: Boolean) {
        emptyText?.isVisible = visible
    }

    override fun setupRecordPauseButton(available: Boolean, isPlaying: Boolean) {
        inputViewController?.setupRecordPauseButton(available, isPlaying)
    }

    override fun displayIniciateKeyExchangeQuestion(@KeyLocationPolicy keyStoragePolicy: Int) {
        MaterialAlertDialogBuilder(requireActivity())
            .setTitle(R.string.key_exchange)
            .setMessage(R.string.you_dont_have_encryption_keys_stored_initiate_key_exchange)
            .setPositiveButton(R.string.button_ok) { _, _ ->
                presenter?.fireIniciateKeyExchangeClick(
                    keyStoragePolicy
                )
            }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    override fun showEncryptionKeysPolicyChooseDialog(requestCode: Int) {
        val view = View.inflate(activity, R.layout.dialog_select_encryption_key_policy, null)
        val buttonOnDisk = view.findViewById<RadioButton>(R.id.button_on_disk)
        val buttonInRam = view.findViewById<RadioButton>(R.id.button_in_ram)

        buttonOnDisk.isChecked = true

        MaterialAlertDialogBuilder(requireActivity())
            .setTitle(R.string.choose_location_key_store)
            .setView(view)
            .setPositiveButton(R.string.button_ok) { _, _ ->
                if (buttonOnDisk.isChecked) {
                    presenter?.fireDiskKeyStoreSelected(requestCode)
                } else if (buttonInRam.isChecked) {
                    presenter?.fireRamKeyStoreSelected(requestCode)
                }
            }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    override fun showEncryptionDisclaimerDialog(requestCode: Int) {
        val view = View.inflate(activity, R.layout.content_encryption_terms_of_use, null)
        MaterialAlertDialogBuilder(requireActivity())
            .setView(view)
            .setTitle(R.string.fenrir_encryption)
            .setPositiveButton(R.string.button_accept) { _, _ ->
                presenter?.fireTermsOfUseAcceptClick(
                    requestCode
                )
            }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        ActivityFeatures.Builder()
            .begin()
            .setHideNavigationMenu(true)
            .setBarsColored(activity, true)
            .build()
            .apply(requireActivity())
    }

    @SuppressLint("ResourceType")
    fun PrepareOptionsMenu(menu: Menu) {

        menu.run {
            findItem(R.id.action_leave_chat).isVisible =
                optionMenuSettings.get(LEAVE_CHAT_VISIBLE, false)
            findItem(R.id.action_edit_chat).isVisible =
                optionMenuSettings.get(CHANGE_CHAT_TITLE_VISIBLE, false)
            findItem(R.id.action_chat_members).isVisible =
                optionMenuSettings.get(CHAT_MEMBERS_VISIBLE, false)
            findItem(R.id.action_key_exchange).isVisible =
                optionMenuSettings.get(KEY_EXCHANGE_VISIBLE, false)
            findItem(R.id.change_hrono_history).isVisible =
                optionMenuSettings.get(HRONO_VISIBLE, false)
            findItem(R.id.show_profile).isVisible = optionMenuSettings.get(PROFILE_VISIBLE, false)
            findItem(R.id.action_invite_link).isVisible =
                optionMenuSettings.get(CAN_GENERATE_INVITE_LINK, false)
        }
        val encryptionStatusItem = menu.findItem(R.id.crypt_state)
        val encryptionStatusVisible = optionMenuSettings.get(ENCRYPTION_STATUS_VISIBLE, false)

        encryptionStatusItem.isVisible = encryptionStatusVisible

        if (encryptionStatusVisible) {
            @DrawableRes
            var drawableRes = R.drawable.ic_outline_lock_open

            if (optionMenuSettings.get(ENCRYPTION_ENABLED, false)) {
                drawableRes = if (optionMenuSettings.get(ENCRYPTION_PLUS_ENABLED, false)) {
                    R.drawable.lock_plus
                } else {
                    R.drawable.ic_outline_lock
                }
            }

            try {
                encryptionStatusItem.setIcon(drawableRes)
            } catch (e: Exception) {
                //java.lang.NullPointerException: Attempt to invoke virtual method
                // 'android.content.res.Resources$Theme android.app.Activity.getTheme()' on a null object reference
            }

        }
    }

    override fun ScrollTo(position: Int) {
        recyclerView?.smoothScrollToPosition(position)
    }

    private val requestWriteScreenshotPermission = AppPerms.requestPermissions(
        this,
        arrayOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
    ) {
        ScreenshotHelper.makeScreenshot(requireActivity())
    }

    private fun downloadChat() {
        MaterialAlertDialogBuilder(requireActivity())
            .setTitle(R.string.confirmation)
            .setMessage(R.string.select_type)
            .setPositiveButton(R.string.to_json) { _, _ -> doDownloadChat("json") }
            .setNegativeButton(R.string.to_html) { _, _ -> doDownloadChat("html") }
            .setNeutralButton(R.string.button_cancel, null)
            .show()
    }

    private val requestWriteChatPermission = AppPerms.requestPermissions(
        this,
        arrayOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
    ) {
        downloadChat()
    }

    private fun OptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.last_read -> {
                recyclerView?.smoothScrollToPosition(presenter?.getConversation()!!.unreadCount)
                return true
            }
            R.id.action_refresh -> {
                recyclerView?.scrollToPosition(0)
                presenter?.reset_Hrono()
                presenter?.fireRefreshClick()
                return true
            }
            R.id.show_profile -> {
                presenter?.fireShow_Profile()
                return true
            }
            R.id.action_short_link -> {
                presenter?.fireShortLinkClick(requireActivity())
                return true
            }
            R.id.change_hrono_history -> {
                recyclerView?.scrollToPosition(0)
                presenter?.invert_Hrono()
                presenter?.fireRefreshClick()
                return true
            }
            R.id.action_leave_chat -> {
                presenter?.fireLeaveChatClick()
                return true
            }
            R.id.delete_chat -> {
                Snackbar.make(requireView(), R.string.delete_chat_do, Snackbar.LENGTH_LONG)
                    .setAction(
                        R.string.button_yes
                    ) { presenter?.removeDialog() }
                    .setAnchorView(InputView)
                    .setBackgroundTint(CurrentTheme.getColorPrimary(requireActivity()))
                    .setActionTextColor(
                        if (Utils.isColorDark(CurrentTheme.getColorPrimary(requireActivity())))
                            Color.parseColor("#ffffff") else Color.parseColor("#000000")
                    ).setTextColor(
                        if (Utils.isColorDark(CurrentTheme.getColorPrimary(requireActivity())))
                            Color.parseColor("#ffffff") else Color.parseColor("#000000")
                    ).show()
                return true
            }
            R.id.action_edit_chat -> {
                presenter?.fireChatTitleClick()
                return true
            }
            R.id.action_chat_members -> {
                presenter?.fireChatMembersClick()
                return true
            }

            R.id.make_screenshot -> {
                if (!AppPerms.hasReadWriteStoragePermission(requireActivity())) {
                    requestWriteScreenshotPermission.launch()
                    return true
                }
                ScreenshotHelper.makeScreenshot(requireActivity())
            }

            R.id.download_peer_to -> {
                if (!AppPerms.hasReadWriteStoragePermission(requireActivity())) {
                    requestWriteChatPermission.launch()
                    return true
                }
                downloadChat()
                return true
            }

            R.id.action_attachments_in_conversation -> presenter?.fireDialogAttachmentsClick()
            R.id.messages_search -> presenter?.fireSearchClick()
            R.id.crypt_state -> presenter?.fireEncriptionStatusClick()
            R.id.action_key_exchange -> presenter?.fireKeyExchangeClick()
            R.id.action_invite_link -> presenter?.fireGenerateInviteLink()
        }

        return super.onOptionsItemSelected(item)
    }

    private fun doDownloadChat(action: String) {
        if (!CheckDonate.isFullVersion(requireActivity())) {
            return
        }
        presenter?.fireChatDownloadClick(requireActivity(), action)
    }

    override fun onInputTextChanged(s: String) {
        presenter?.fireDraftMessageTextEdited(s)
        presenter?.fireTextEdited(s)
    }

    override fun onSendClicked(body: String) {
        presenter?.fireSendClick()
    }

    override fun onMyStickerClick(file: Sticker.LocalSticker) {
        if (!CheckDonate.isFullVersion(requireActivity())) {
            return
        }
        presenter?.fireSendMyStickerClick(file)
    }

    override fun onAttachClick() {
        presenter?.fireAttachButtonClick()
    }

    override fun onBackPressed(): Boolean {
        if (actionModeHolder?.isVisible() == true) {
            actionModeHolder?.hide()
            return false
        }

        if (inputViewController?.onBackPressed() == false) {
            return false
        }

        if (presenter!!.inInverHrono) {
            presenter?.reset_Hrono()
            presenter?.fireRefreshClick()
            return false
        }

        return presenter?.onBackPressed() == true
    }

    private fun isActionModeVisible(): Boolean {
        return actionModeHolder?.rootView?.visibility == View.VISIBLE
    }

    override fun onMessageDelete(message: Message) {
        val ids: ArrayList<Message> = ArrayList()
        ids.add(message)
        presenter?.fireDeleteForMeClick(ids)
    }

    override fun onAvatarClick(message: Message, userId: Int) {
        if (isActionModeVisible()) {
            presenter?.fireMessageClick(message)
        } else {
            presenter?.fireOwnerClick(userId)
        }
    }

    override fun onRestoreClick(message: Message, position: Int) {
        presenter?.fireMessageRestoreClick(message)
    }

    override fun onBotKeyboardClick(button: Keyboard.Button) {
        presenter?.fireBotSendClick(button, requireActivity())
    }

    override fun onMessageLongClick(message: Message): Boolean {
        presenter?.fireMessageLongClick(message)
        return true
    }

    override fun onMessageClicked(message: Message) {
        presenter?.fireMessageClick(message)
    }

    override fun onLongAvatarClick(message: Message, userId: Int) {
        presenter?.fireLongAvatarClick(userId)
    }

    override fun didPressedButton(button: Keyboard.Button, needClose: Boolean) {
        presenter?.fireBotSendClick(button, requireActivity())
        if (needClose) {
            inputViewController?.closeBotKeyboard()
        }
    }

    override fun copyToClipBoard(link: String) {
        val clipboard =
            requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("response", link)
        clipboard.setPrimaryClip(clip)
        CustomToast.CreateCustomToast(requireActivity()).showToast(R.string.copied_to_clipboard)
    }

    fun saveState() {
        inputViewController?.storeEmoji()
        presenter?.saveDraftMessageBody()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        inputViewController?.destroyView()
        inputViewController = null
    }


    companion object {
        fun newInstance(accountId: Int, messagesOwnerId: Int, peer: Peer): ChatFragment {
            val args = Bundle()
            args.putInt(Extra.ACCOUNT_ID, accountId)
            args.putInt(Extra.OWNER_ID, messagesOwnerId)
            args.putParcelable(Extra.PEER, peer)

            val fragment = ChatFragment()
            fragment.arguments = args
            return fragment
        }

        private const val LEAVE_CHAT_VISIBLE = 1
        private const val CHANGE_CHAT_TITLE_VISIBLE = 2
        private const val CHAT_MEMBERS_VISIBLE = 3
        private const val ENCRYPTION_STATUS_VISIBLE = 4
        private const val ENCRYPTION_ENABLED = 5
        private const val ENCRYPTION_PLUS_ENABLED = 6
        private const val KEY_EXCHANGE_VISIBLE = 7
        private const val HRONO_VISIBLE = 8
        private const val PROFILE_VISIBLE = 9
        private const val CAN_GENERATE_INVITE_LINK = 10
    }
}
