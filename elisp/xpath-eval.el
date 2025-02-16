;;; xpath-eval --- Support for evaluating XPath expressions against XML documents -*- lexical-binding: t; -*-
;;
;; Copyright (C) 2025 Norm Tovey-Walsh
;;
;; Author: Norm Tovey-Walsh <norm@tovey-walsh.com>
;; Created 26 February 2025
;;
;; Keywords: XML, XPath
;; URL: https://github.com/ndw/xpath-server
;;
;;
;; This file is not part of GNU Emacs.
;;
;; This file is free software. It is distributed with the MIT license.

;;; Code:

(defcustom xpath-server-xpath-server-port 5078
  "The server port number.
The XPath server must be listening on this port."
  :group 'xpath-server
  :type 'integer)

(defun xpath-server--server-uri (path)
  "Construct a server uri from PATH and the port number."
  (concat "http://localhost:"
          (number-to-string xpath-server-xpath-server-port)
          path))

(defun xpath-server--post-document ()
  "Post the current buffer to the server."
  (let ((url-request-method "POST")
        (url-request-extra-headers `(("Content-Type" . "application/xml")))
        (url-request-data          (encode-coding-string (buffer-string) 'utf-8))
        (url (xpath-server--server-uri (concat "/document?uri=" (buffer-file-name)))))
    (let ((upload (url-retrieve-synchronously url)))
      (save-excursion
        (with-current-buffer upload
          (beginning-of-buffer)
          (search-forward "OK XML")))
      (kill-buffer upload))))

(defun xpath-server--post-xpath (xpath)
  "Post the XPATH expression to the server."
  (let ((url-request-method "POST")
        (url-request-extra-headers `(("Content-Type" . "application/xpath")))
        (url-request-data          (encode-coding-string xpath 'utf-8)))
    (url-retrieve (xpath-server--server-uri "/xpath") 'xpath-server--switch-to-url-buffer)))

(defun xpath-server-xpath-eval (xpath)
  "Find the nodes that match XPATH in the current document."
  (interactive "MEnter XPath: ")
  (let ((ok (xpath-server--post-document)))
    (if ok
        (xpath-server--post-xpath xpath)
      (message "Upload failed"))))

(defun xpath-server-xpath-xmlns (prefix uri)
  "Declare a namespace binding for future queries."
  (interactive "MEnter prefix: \nMEnter namespace name: ")
  (let ((url-request-method "POST")
        (url (xpath-server--server-uri (concat "/namespace/" prefix "?uri=" uri))))
    (let ((upload (url-retrieve-synchronously url))
          (start 0))
      (save-excursion
        (with-current-buffer upload
          (beginning-of-buffer)
          (search-forward "xmlns:")
          (backward-char 6)
          (setq start (point))
          (end-of-buffer)
          (message (buffer-substring start (point)))))
      (kill-buffer upload))))

(defun xpath-server-xpath-namespaces ()
  "List the current namespace bindings."
  (interactive)
  (let ((url-request-method "GET")
        (url (xpath-server--server-uri "/namespaces")))
    (let ((upload (url-retrieve-synchronously url))
          (start 0))
      (save-excursion
        (with-current-buffer upload
          (beginning-of-buffer)
          (search-forward "\n\n")
          (setq start (point))
          (end-of-buffer)
          (message (buffer-substring start (point)))))
      (kill-buffer upload))))

(defun xpath-server--goto-element ()
  "Navigate to the current match in the original document."
  (interactive)
  (let ((prefix (save-excursion
                  (beginning-of-buffer)
                  (buffer-substring 1 (search-forward ":"))))
        (xmlbuffer (save-excursion
                     (beginning-of-buffer)
                     (let ((start (search-forward "href="))
                           (end 1))
                       (forward-char)
                       (setq end (search-forward "\""))
                       (get-file-buffer (buffer-substring (1+ start) (1- end))))))
        (start 0)
        (end 0)
        (lineno 0))
    (search-backward (concat prefix "result "))
    (search-forward "line=")
    (forward-char)
    (setq start (point))
    (setq end (search-forward "\""))
    (setq lineno (buffer-substring start (1- end)))
    (switch-to-buffer xmlbuffer)
    (goto-line (string-to-number lineno))))

(defun xpath-server--goto-element-and-kill-buffer ()
  "Navigate to the current match, then kill the results buffer."
  (interactive)
  (let ((xmlbuffer (current-buffer)))
    (xpath-server--goto-element)
    (kill-buffer xmlbuffer)))

(defun xpath-server--switch-to-url-buffer (status)
  "Construct a results buffer from the server response."
  (let ((xml (save-excursion
               (beginning-of-buffer)
               (search-forward "\n\n")
               (mark)
               (end-of-buffer)
               (buffer-substring (mark) (point))))
        (result-buffer (create-file-buffer "*XPath results*")))
    (switch-to-buffer result-buffer)
    (nxml-mode)
    (insert (decode-coding-string xml 'utf-8))
    (beginning-of-buffer)
    (if (search-forward ":result " nil t)
        (progn
          (next-line)
          (beginning-of-line)
          (read-only-mode)
          (use-local-map (copy-keymap nxml-mode-map))
          (local-set-key "g" 'xpath-server--goto-element)
          (local-set-key "G" 'xpath-server--goto-element-and-kill-buffer))
      (progn
        (kill-buffer result-buffer)
        (message "No matches.")))))

;;; xpath-eval.el ends here
