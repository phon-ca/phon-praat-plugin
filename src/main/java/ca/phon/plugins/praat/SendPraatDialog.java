package ca.phon.plugins.praat;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import ca.hedlund.jpraat.binding.sys.SendPraat;
import ca.phon.app.log.BufferPanel;
import ca.phon.app.log.BufferWindow;
import ca.phon.app.session.editor.SessionEditor;
import ca.phon.app.session.editor.view.waveform.WaveformEditorView;
import ca.phon.plugins.praat.script.PraatScript;
import ca.phon.plugins.praat.script.PraatScriptContext;
import ca.phon.plugins.praat.script.PraatScriptTcpHandler;
import ca.phon.plugins.praat.script.PraatScriptTcpServer;
import ca.phon.session.MediaSegment;
import ca.phon.session.Record;
import ca.phon.session.SessionFactory;
import ca.phon.ui.CommonModuleFrame;
import ca.phon.ui.action.PhonUIAction;
import ca.phon.ui.decorations.DialogHeader;
import ca.phon.ui.toast.ToastFactory;
import ca.phon.util.icons.IconManager;
import ca.phon.util.icons.IconSize;

public class SendPraatDialog extends CommonModuleFrame {
	
	private static final Logger LOGGER = Logger
			.getLogger(SendPraatDialog.class.getName());

	private static final long serialVersionUID = -650429914705807269L;
	
	private static final String TEMPLATE_FILE = "SendPraatTemplate.vm";
	
	private final SessionEditor editor;
	
	private JTextArea textPane;
	
	private JButton sendPraatButton;
	
	private JButton previewButton;

	private JCheckBox waitForResponseBox;
	
	public SendPraatDialog(SessionEditor editor) {
		super();
		setWindowName("SendPraat");
		setParentFrame(editor);
		
		this.editor = editor;
		init();
	}
	
	private void init() {
		setLayout(new BorderLayout());
		
		final DialogHeader header = new DialogHeader("SendPraat", "Execute script in Praat");
		add(header, BorderLayout.NORTH);
		
		final JPanel contentPane = new JPanel(new BorderLayout());
		
		final JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		waitForResponseBox = new JCheckBox("Listen for response");
//		topPanel.add(waitForResponseBox);
		
		contentPane.add(topPanel, BorderLayout.NORTH);
		
		textPane = new JTextArea();
		textPane.setWrapStyleWord(false);
		
		textPane.setText(loadTemplate().getScriptText());
		final JScrollPane scroller = new JScrollPane(textPane);
		contentPane.add(scroller, BorderLayout.CENTER);
		
		final JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		
		final PhonUIAction sendPraatAct = new PhonUIAction(this, "onSendPraat");
		sendPraatAct.putValue(PhonUIAction.NAME, "SendPraat");
		sendPraatAct.putValue(PhonUIAction.SMALL_ICON, IconManager.getInstance().getIcon("apps/praat", IconSize.SMALL));
		sendPraatButton = new JButton(sendPraatAct);
		btnPanel.add(waitForResponseBox);
		btnPanel.add(sendPraatButton);
		contentPane.add(btnPanel, BorderLayout.SOUTH);
		
		add(contentPane, BorderLayout.CENTER);
	}
	
	public void onSendPraat() {
		final PraatScriptTcpHandler handler = new PraatScriptTcpHandler() {
			
			@Override
			public void praatScriptFinished(final String data) {
				if(data == null || data.trim().length() == 0) return;
				final Runnable runnable = new Runnable() {
					
					@Override
					public void run() {
						final BufferWindow bw = BufferWindow.getInstance();
						if(!bw.isVisible()) {
							bw.setSize(500, 600);
							bw.centerWindow();
							bw.setVisible(true);
						} else {
							bw.requestFocus();
						}
						
						final BufferPanel bp = bw.createBuffer("SendPraat");
						final PrintWriter pw = new PrintWriter(bp.getLogBuffer().getStdOutStream());
						pw.write(data);
						pw.flush();
						pw.close();
					}
				};
				if(SwingUtilities.isEventDispatchThread())
					runnable.run();
				else
					SwingUtilities.invokeLater(runnable);
			}
	
		};
		
		PraatScriptTcpServer server = null;
		String script = new String();
		if(waitForResponseBox.isSelected()) {
			server = new PraatScriptTcpServer();
			server.setHandler(handler);
			server.startServer();
			script = generateScript(server);
		} else {
			script = generateScript();
		}
		
		final String err = SendPraat.sendpraat(null, "Praat", 0, script);
		if(err != null && err.length() > 0) {
			if(waitForResponseBox.isSelected() && server != null) {
				server.stop();
			}
			ToastFactory.makeToast(err).start(textPane);
		}
	}
	
	public String generateScript(PraatScriptTcpServer server) {
		final PraatScript script = new PraatScript(textPane.getText());
		final PraatScriptContext ctx = createContext();
		ctx.put("socket", server.getPort());
		
		String scriptText = new String();
		try {
			scriptText = script.generateScript(ctx);
		} catch (IOException e) {
			LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
			ToastFactory.makeToast(e.getLocalizedMessage()).start(textPane);
		}
		return scriptText;
	}
	
	public String generateScript() {
		final PraatScript script = new PraatScript(textPane.getText());
		final PraatScriptContext ctx = createContext();
		
		String scriptText = new String();
		try {
			scriptText = script.generateScript(ctx);
		} catch (IOException e) {
			LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
			ToastFactory.makeToast(e.getLocalizedMessage()).start(textPane);
		}
		return scriptText;
	}
	
	private PraatScript loadTemplate() {
		final StringBuilder builder = new StringBuilder();
		try {
			final InputStream is = getClass().getResourceAsStream(TEMPLATE_FILE);
			final InputStreamReader reader = new InputStreamReader(is, "UTF-8");
			final char[] buffer = new char[1024];
			int len = -1;
			while((len = reader.read(buffer)) > 0) {
				builder.append(buffer, 0, len);
			}
			is.close();
		} catch (IOException e) {
			LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}
		return new PraatScript(builder.toString());
	}
	
	private PraatScriptContext createContext() {
		final PraatScriptContext context = new PraatScriptContext();
		context.put("session", editor.getSession());

		final Record r = editor.currentRecord();
		final MediaSegment seg =
				(r.getSegment().numberOfGroups() > 0 ? r.getSegment().getGroup(0) : (SessionFactory.newFactory().createMediaSegment()));
		context.put("record", r);
		context.put("segment", seg);
		
		final WaveformEditorView waveformView = 
				(editor.getViewModel().isShowing(WaveformEditorView.VIEW_TITLE) ?
						(WaveformEditorView)editor.getViewModel().getView(WaveformEditorView.VIEW_TITLE) :
							null);
		if(waveformView != null) {
			final File audioFile = waveformView.getAudioFile();
			if(audioFile != null) {
				context.put("audioPath", audioFile.getAbsolutePath());
			}
			
			final double selStart = 
					waveformView.getWavDisplay().get_selectionStart();
			final double selEnd =
					waveformView.getWavDisplay().get_selectionEnd();
			if(selStart >= 0 && selEnd > selStart) {
				final MediaSegment sel = (SessionFactory.newFactory().createMediaSegment());
				sel.setStartValue((float)selStart);
				sel.setEndValue((float)selEnd);
				context.put("selection", sel);
			}
		}
		
		final TextGridManager manager =
				TextGridManager.getInstance(editor.getProject());
		context.put("textGridPath", manager.textGridPath(r.getUuid().toString()));
		
		context.put("spectrogramSettings", new SpectrogramSettings());
		context.put("formantSettingsd", new FormantSettings());
		context.put("pitchSettings", new PitchSettings());
		
		context.put("replyToPhon", waitForResponseBox.isSelected());
		
		return context;
	}
	
}
