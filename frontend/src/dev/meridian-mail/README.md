# Meridian-style Mail preview

Open `/meridian-preview.html` with the Vite development server. This is a separate HTML entry, imported only by its own page. Vite's production build includes only `index.html`; the preview page and fixtures are absent from `dist`.

## Source-grounded layout

The supplied Meridian HTML uses `shell()` for the dark application sidebar and Mail/Platform composition, `mailPane()` for the ribbon and body, `ribbon()` for tabs/search/commands, `folderPane()` for favourites and account folders, `listPane()`/`row()` for the dense list, and `readView()`/`mailBanner()` for the reader and contextual actions. Its CSS specifies a 236px sidebar, 52px application rail, 212px folder pane, 380px full-mail message list, 34px folder rows, 34px avatars, 13px sender/subject, 12px preview/time, 20px list title, and 18px reading subject. It uses a two-level ribbon, panel borders, container queries and independent Mail/Platform scrolling. Side by side places expanded reading content immediately after its message row.

This preview keeps the FreightFlow 196px labeled sidebar and green accent. Its two-level ribbon, rail, folder, list, reader and inline composition use the Meridian hierarchy and type scale. The preview has no production links or API integrations. Disabled rail destinations and Sync make unavailable capabilities explicit.

## Component tree

`Preview` owns local fixture state and composes `MailRibbon`, `MailAppRail`, `MailFolderPane`, `MailMessageList`/`MailMessageRow`, `MailReadingPane`/`MailInlineReader`, `MailBusinessActions`, and `MailIntelligencePanel`. Components accept typed data and callbacks. The `PreviewMessage` and `PreviewFolder` interfaces are the future mapping seam for real APIs; business actions are semantic callbacks. The isolated `preview.css` owns all `mmp-` selectors and design tokens.

## Review scenarios

Use `?mode=full&message=unknown`, `?mode=full&message=rate`, `?mode=full&message=accepted`, `?mode=split&message=unknown`, or `?mode=split&message=unknown&expanded=unknown`. Search, All/Unread, sort, folders, tabs, layout switch, inline reader, customer/enquiry context and editable onboarding fields use local state. No save, send, sync or download is simulated.

## Integration plan

After visual approval, map real Mail API messages/folders into the typed props, pass selected message/mailbox IDs and read state from the existing workspace, then bind semantic callbacks to existing compose/reply, customer, enquiry, AI and attachment handlers. Add loading/error props and quotation context as needed. Keep security and ownership checks in the existing API layer. Integrate one pane at a time with browser viewport regression checks before replacing the current Mail UI.
