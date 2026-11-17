package com.yourgpt.ai;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {

    private LinearLayout messages;
    private EditText input;
    private ScrollView scroll;

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private void addMessage(String value, boolean user) {
        TextView bubble = text(value, 15, Color.WHITE);
        bubble.setPadding(dp(14), dp(11), dp(14), dp(11));
        bubble.setBackgroundColor(user ? 0xFF173D36 : 0xFF171717);

        LinearLayout.LayoutParams params =
            new LinearLayout.LayoutParams(
                -2, -2
            );
        params.gravity = user ? Gravity.END : Gravity.START;
        params.setMargins(dp(8), dp(6), dp(8), dp(6));

        messages.addView(bubble, params);
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(dp(14), dp(12), dp(14), dp(10));

        TextView title = text("Your GPT", 25, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);

        TextView subtitle = text(
            "PRIVATE  •  OFFLINE AI ASSISTANT",
            10, 0xFF55C7A5
        );
        subtitle.setPadding(0, dp(4), 0, dp(14));

        root.addView(title);
        root.addView(subtitle);

        scroll = new ScrollView(this);
        messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(messages);

        root.addView(scroll, new LinearLayout.LayoutParams(
            -1, 0, 1
        ));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(0, dp(10), 0, 0);

        input = new EditText(this);
        input.setSingleLine(false);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(0xFF888888);
        input.setHint("Ask Your GPT anything...");
        input.setTextSize(15);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setBackgroundColor(0xFF171717);

        bottom.addView(input, new LinearLayout.LayoutParams(
            0, dp(52), 1
        ));

        Button send = new Button(this);
        send.setText("SEND");
        send.setTextColor(0xFF55C7A5);
        send.setBackgroundTintList(
            android.content.res.ColorStateList.valueOf(0xFF173D36)
        );

        bottom.addView(send);

        send.setOnClickListener(v -> {
            String message = input.getText().toString().trim();
            if (!message.isEmpty()) {
                addMessage(message, true);
                input.setText("");
                addMessage(
                    "Offline AI engine is not connected yet. Model integration is the next development step.",
                    false
                );
            }
        });

        root.addView(bottom);
        setContentView(root);

        addMessage(
            "Welcome to Your GPT. Your private AI assistant is being prepared.",
            false
        );
    }
}
