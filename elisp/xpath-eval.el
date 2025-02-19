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
        (url (xpath-server--server-uri (concat "/document/" (buffer-file-name)))))
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

(defun xpath-server-watch (arg)
  "Watch the directory that the current buffer is visiting."
  (interactive "P")
  (if (buffer-file-name) 
      (let ((url-request-method "POST")
            (uri (xpath-server--server-uri (concat "/watch" (file-name-directory (buffer-file-name))))))
        (let ((watch
               (if arg
                   (url-retrieve-synchronously (concat uri "?stop"))
                 (url-retrieve-synchronously uri))))
          (save-excursion
            (with-current-buffer watch
              (beginning-of-buffer)
              (search-forward "\n\n")
              (message (buffer-substring (point) (point-max)))))
          (kill-buffer watch)))))

(defun xpath-server-search (xpath)
  "Find the nodes that match XPATH in the current document."
  (interactive "MEnter XPath: ")
  (let ((url-request-method "POST")
        (url-request-extra-headers `(("Content-Type" . "application/xpath")))
        (url-request-data (encode-coding-string xpath 'utf-8)))
    (url-retrieve (xpath-server--server-uri "/search") 'xpath-server--switch-to-url-buffer)))

(defun xpath-server-status (arg)
  "Report the server status."
  (interactive "P")
  (let ((url-request-method "GET")
        (uri (if arg
                 (xpath-server--server-uri "/?debug=1")
               (xpath-server--server-uri "/"))))
    (let ((status (url-retrieve-synchronously uri)))
      (if arg
          (with-output-to-temp-buffer "XPath server status"
            (with-current-buffer status
              (beginning-of-buffer)
              (search-forward "\n\n")
              (princ (buffer-substring (point) (point-max)))))
        (with-current-buffer status
          (beginning-of-buffer)
          (search-forward "\n\n")
          (message (buffer-substring (point) (point-max))))))))

(defun xpath-server-query (xpath)
  "Find the nodes that match XPATH in the current document."
  (interactive "MEnter XPath: ")
  (let ((ok (xpath-server--post-document)))
    (if ok
        (xpath-server--post-xpath xpath)
      (message "Upload failed"))))

(defun xpath-server-namespaces ()
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

(defun xpath-server-xmlns (prefix uri)
  "Declare a namespace binding for future queries."
  (interactive "MEnter prefix: \nMEnter uri: ")
  (let ((url-request-method "POST")
        (url (xpath-server--server-uri (concat "/namespace/" prefix)))
        (url-request-data (encode-coding-string uri 'utf-8)))
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
  (let* ((cur-line (count-lines 1 (point)))
         (prefix (save-excursion
                   (beginning-of-buffer)
                   (buffer-substring 1 (search-forward ":"))))
         (result (save-excursion
                   (search-backward (concat prefix "result ") nil t)))
         (start-line (if result
                         (count-lines 1 result)
                       nil))
         (filename (if result
                       (save-excursion
                         (search-backward (concat prefix "path "))
                         (let ((start (search-forward "href="))
                               (end 1))
                           (forward-char)
                           (setq end (search-forward "\""))
                           (buffer-substring (1+ start) (1- end))))
                     nil))
         (first-line (if (and filename result)
                         (save-excursion
                           (goto-char result)
                           (search-forward "line=")
                           (forward-char)
                           (setq start (point))
                           (setq end (search-forward "\""))
                           (string-to-number (buffer-substring start (1- end))))
                       nil)))
     (if (and filename result)
        (progn
          (find-file filename)
          (goto-line (1- (+ first-line (- cur-line start-line)))))
      (message "Not in a result."))))

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

(provide 'xpath-eval)

;;; xpath-eval.el ends here
