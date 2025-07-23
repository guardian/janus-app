import DOMPurify from "dompurify";
import M from "materialize-css";
import { getCsrfTokenFromMetaTag } from "./utils/csrf.js";
import { deletePasskey, registerPasskey } from "./utils/passkeys.js";
import {
  displayFlashMessages,
  displayToast,
  messageType,
} from "./utils/toastMessages.js";

document.addEventListener("DOMContentLoaded", function () {
  "use strict";

  const csrfToken = getCsrfTokenFromMetaTag();

  //Initialise Materialize elements
  const sidenavElems = document.querySelectorAll(".sidenav");
  // eslint-disable-next-line no-unused-vars -- required by Materialize
  const sidenavInstances = M.Sidenav.init(sidenavElems);
  const modalElems = document.querySelectorAll(".modal");
  // eslint-disable-next-line no-unused-vars -- required by Materialize
  const modalInstances = M.Modal.init(modalElems);
  M.updateTextFields();
  const collapsibleElems = document.querySelectorAll(".collapsible");
  // eslint-disable-next-line no-unused-vars -- required by Materialize
  const collapsibleInstances = M.Collapsible.init(collapsibleElems);
  const dropdownElems = document.querySelectorAll(".dropdown-trigger");
  // eslint-disable-next-line no-unused-vars -- required by Materialize
  const dropdownInstances = M.Dropdown.init(dropdownElems);
  const tooltipElems = document.querySelectorAll(".tooltipped");
  // eslint-disable-next-line no-unused-vars -- required by Materialize
  const instances = M.Tooltip.init(tooltipElems);

  // aws-profile-name
  if (document.querySelectorAll(".editable-aws-profile").length) {
    const profileIdContainers = document.querySelectorAll(
      ".editable-aws-profile .aws-profile-id",
    );
    document.querySelectorAll("#aws-profile-id").forEach(function (input) {
      input.addEventListener("keyup", function () {
        profileIdContainers.forEach(function (el) {
          let lines;
          if (el.tagName === "TEXTAREA") {
            lines = el.value.split("\n");
          } else {
            lines = el.innerText.split("\n");
          }
          const replaced = lines.map(function (line) {
            return line.replace(
              /--profile [a-zA-Z0-9-]*/,
              "--profile " + input.value,
            );
          });
          if (el.tagName === "TEXTAREA") {
            el.value = replaced.join("\n");
          } else {
            el.innerText = replaced.join("\n");
          }
        });
      });
    });
  }

  // copy-text
  document.querySelectorAll(".copy-text--button").forEach(function (button) {
    const container = button.closest(".copy-textarea"),
      textAreaTarget = container.querySelector("textarea"),
      defaultCopyTextIcon = container.querySelector(".copy-text--default"),
      confirmationIcon = container.querySelector(".copy-text--confirm"),
      warnIcon = container.querySelector(".copy-text--warn");

    button.addEventListener("click", function (e) {
      e.preventDefault();
      try {
        textAreaTarget.select();
        navigator.clipboard
          .writeText(textAreaTarget.value)
          .then(function () {
            confirmationIcon.style.display = "inline";
            defaultCopyTextIcon.style.display = "none";
            setTimeout(function () {
              confirmationIcon.style.display = "none";
              warnIcon.style.display = "none";
              defaultCopyTextIcon.style.display = "inline";
            }, 4000);
          })
          .catch(function (err) {
            warnIcon.style.display = "inline";
            defaultCopyTextIcon.style.display = "none";
            throw err;
          });
      } catch (err) {
        warnIcon.style.display = "inline";
        defaultCopyTextIcon.style.display = "none";
        throw err;
      }
    });
  });

  // index page controls are fixed until user scrolls to footer
  document.querySelectorAll(".controls__hero").forEach(function () {
    const win = window,
      controlContainer = document.querySelector(".controls__hero"),
      footer = document.querySelector("footer"),
      recalculate = function () {
        const lowestVisiblePixel = win.scrollY + win.innerHeight,
          footerPosition = footer.getBoundingClientRect().top + win.scrollY;

        if (lowestVisiblePixel > footerPosition) {
          controlContainer.style.position = "relative";
          footer.style.marginTop = 0;
        } else {
          controlContainer.style.position = "fixed";
          footer.style.marginTop = controlContainer.offsetHeight + "px";
        }
      };
    // enable feature if JS is available
    controlContainer.style.display = "block";

    win.addEventListener("scroll", recalculate);
    win.addEventListener("resize", recalculate);
    recalculate();
  });

  // allow user to simultaneously obtain credentials from multiple accounts
  document
    .querySelectorAll(".multiple-credentials-control__container")
    .forEach(function (container) {
      const checkboxes = document.querySelectorAll(".multi-select__checkbox"),
        singleCredentialsLinks = document.querySelectorAll(
          ".federation__link--credentials",
        ),
        activeContainer = document.querySelector(
          ".multiple-credentials-control--active",
        ),
        inactiveContainer = document.querySelector(
          ".multiple-credentials-control--inactive",
        ),
        controlFeedbackText = document.querySelector(".multi-accounts-count"),
        accountContainers = document.querySelectorAll(".card--aws-account"),
        clearButton = document.querySelector(
          ".multiple-credentials-control--clear",
        ),
        updateLink = function (permissions) {
          const link = document.querySelector(".multiple-credentials__link"),
            hasPermissions = link.href.indexOf("permissionIds=") !== -1,
            permissionStr = permissions.map(encodeURIComponent).join(",");

          if (hasPermissions) {
            link.href = link.href.replace(
              /permissionIds=[^&]*/,
              "permissionIds=" + permissionStr,
            );
          } else {
            link.href += "&permissionIds=" + permissionStr;
          }
        },
        updateSelection = function (event) {
          const checked = Array.from(checkboxes).filter(function (checkbox) {
              return checkbox.checked;
            }),
            permissions = checked.map(function (checkbox) {
              return DOMPurify.sanitize(
                checkbox.getAttribute("data-permission-id"),
              );
            }),
            clicked = event ? event.target : null,
            accountContainer = clicked
              ? clicked.closest(".aws-account-body")
              : null,
            otherAccountCheckboxes = accountContainer
              ? accountContainer.querySelectorAll(
                  ".multi-select__checkbox:not(:checked)",
                )
              : [];

          // enable feature if JS is available
          document
            .querySelectorAll(".multi-select__container")
            .forEach((el) => (el.style.display = "block"));

          // deal with disabling other permissions in the same account as the one just selected
          if (event) {
            if (clicked.checked) {
              otherAccountCheckboxes.forEach(function (checkbox) {
                checkbox.checked = false;
                checkbox.disabled = true;
              });
            } else {
              otherAccountCheckboxes.forEach(function (checkbox) {
                checkbox.checked = false;
                checkbox.disabled = false;
              });
            }
          }

          // update display
          if (permissions.length) {
            activeContainer.style.display = "block";
            inactiveContainer.style.display = "none";
            // disable all non-multi credentials links to avoid confusion
            singleCredentialsLinks.forEach(function (link) {
              link.classList.add("disabled");
            });
            accountContainers.forEach(function (container) {
              container.style.filter = "grayscale(0)";
            });
            Array.from(accountContainers)
              .filter(function (container) {
                return !checked.some(function (checkbox) {
                  return container.contains(checkbox);
                });
              })
              .forEach(function (container) {
                container.style.filter = "grayscale(0.6)";
              });
          } else {
            inactiveContainer.style.display = "block";
            activeContainer.style.display = "none";
            // restore single credentials links
            singleCredentialsLinks.forEach(function (link) {
              link.classList.remove("disabled");
            });
            accountContainers.forEach(function (container) {
              container.style.filter = "grayscale(0)";
            });
          }
          controlFeedbackText.textContent =
            permissions.length === 1
              ? permissions.length + " account"
              : permissions.length + " accounts";
          updateLink(permissions);
        };

      container.style.display = "block";
      checkboxes.forEach(function (checkbox) {
        checkbox.addEventListener("change", updateSelection);
      });
      clearButton.addEventListener("click", function () {
        checkboxes.forEach(function (checkbox) {
          checkbox.checked = false;
          checkbox.disabled = false;
        });
        updateSelection();
      });
      updateSelection();
    });
  // add timezone to federation links
  document.querySelectorAll(".federation__link").forEach(function (el) {
    const link = el,
      tzOffset = (new Date().getTimezoneOffset() * -1) / 60;
    link.href = link.href + "&tzOffset=" + tzOffset;
  });
  // login lease time
  document
    .querySelectorAll(".login-duration__container")
    .forEach(function (container) {
      const defaultLongDurationLink = document.querySelector(
          ".dropdown-time__link--default[data-length=standard]",
        ),
        links = container.querySelectorAll(".time-link"),
        maxLongDuration = document.querySelector(
          ".dropdown-time__link--max[data-length=standard]",
        ).dataset.duration,
        endOfWorkSeconds = (function () {
          let ms,
            endOfWork = new Date();
          endOfWork.setHours(19);
          endOfWork.setMinutes(0);
          endOfWork.setSeconds(0);
          ms = endOfWork.getTime();
          if (Date.now() - endOfWork > 0) {
            return ms + 86400 * 1000;
          } else {
            return ms;
          }
        })(),
        msToEndOfWork = endOfWorkSeconds - Date.now(),
        updateHrefDurations = function (duration, shortTerm) {
          const selector =
            ".federation__link--" + (shortTerm ? "short" : "standard");
          document.querySelectorAll(selector).forEach(function (link) {
            const hasDuration = link.href.indexOf("duration=") !== -1;
            if (hasDuration) {
              link.href = link.href.replace(
                /duration=[^&]*/,
                "duration=" + duration,
              );
            } else {
              link.href = link.href + "&duration=" + duration;
            }
          });
        };

      // enable feature if JS is available
      links.forEach(function (link) {
        link.removeAttribute("disabled");
      });

      // add click handlers to time choices
      document
        .querySelectorAll(".dropdown-time__link")
        .forEach(function (link) {
          link.addEventListener("click", function (e) {
            e.preventDefault();
            // update hrefs
            updateHrefDurations(
              link.dataset.duration,
              "short" === link.dataset.length,
            );
            // update button text
            link
              .closest(".login-duration__header")
              .querySelector(".dropdown-trigger").textContent =
              link.textContent;
          });
        });

      // remove wallclock option if we're too far from 19:00
      if (msToEndOfWork > maxLongDuration) {
        const walltimeSelector = document.querySelector(
          ".dropdown-time__link--walltime[data-length=standard]",
        );
        if (walltimeSelector) {
          walltimeSelector
            .closest(".login-duration__header")
            .querySelector(".dropdown-trigger").textContent =
            defaultLongDurationLink.textContent;
          walltimeSelector.remove();
        }
      }

      // remove admin control if there are no admin permissions available
      if (document.querySelectorAll(".federation__link--short").length === 0) {
        document.querySelector(".login-duration--admin").style.display = "none";
      }
    });

  // local times
  document.querySelectorAll(".local-date").forEach(function (el) {
    const dateSpan = el,
      datestamp = dateSpan.getAttribute("data-date"),
      pad = function (n, width, char) {
        char = char || "0";
        n = String(n);
        return n.length >= width
          ? n
          : new Array(width - n.length + 1).join(char) + n;
      };
    if (datestamp) {
      const d = new Date(Date.parse(datestamp));
      dateSpan.textContent =
        pad(d.getHours(), 2) +
        ":" +
        pad(d.getMinutes(), 2) +
        ":" +
        pad(d.getSeconds(), 2);
    }
  });
  // adjust for windows OS
  document
    .querySelectorAll(".textarea--code.aws-profile-id")
    .forEach(function (el) {
      if (navigator.userAgent.includes("Win")) {
        // TODO: test this change in Windows
        const winCmd = el.value.replace(/\\\n/g, "^\n").replace(/^ /gm, "");
        el.value = winCmd;
      }
    });
  {
    // disable old auto-logout cookie to tidy up
    // this can be removed in the near future, when this cookie will have been cleared out of colleague's browsers
    const COOKIE__AUTO_LOGOUT = "janus_auto_logout";
    document.cookie = `${COOKIE__AUTO_LOGOUT}=; expires=Thu, 01 Jan 1970 12:00:00 UTC; path=/`;
  }

  /**
   * Sets up click event listener for the passkey registration button
   * @param {string} selector - CSS selector for the register button
   * @param {string} csrfToken - CSRF token for security verification
   */
  function setUpRegisterPasskeyButton(selector, csrfToken) {
    const registerButton = document.querySelector(selector);
    if (!registerButton) {
      return;
    }

    registerButton?.addEventListener("click", function (e) {
      e.preventDefault();
      registerPasskey(csrfToken).catch(function (err) {
        console.error("Error setting up register passkey button:", err);
      });
    });
  }

  /**
   * Sets up click event listeners for passkey deletion buttons
   * @param {string} selector - CSS selector for delete buttons
   * @param {string} csrfToken - CSRF token for security verification
   */
  function setUpDeletePasskeyButtons(selector, csrfToken) {
    const deleteButtons = document.querySelectorAll(selector);

    deleteButtons.forEach((button) => {
      button.addEventListener("click", async () => {
        const passkeyName = button.getAttribute("data-passkey-name");
        const passkeyId = button.getAttribute("data-passkey-id");

        if (!passkeyId) {
          console.error("No passkey ID found");
          displayToast("Error: Passkey ID not found", messageType.error);
          return;
        }

        if (
          confirm(
            `Are you sure you want to delete the passkey "${DOMPurify.sanitize(passkeyName)}"?`,
          )
        ) {
          const result = await deletePasskey(passkeyId, csrfToken);
          // Immediately redirect to the user-account page
          // The flash message will be displayed after the redirect
          if (result.redirect) {
            window.location.href = result.redirect;
          } else {
            window.location.reload();
          }
        }
      });
    });
  }

  try {
    setUpRegisterPasskeyButton("#register-passkey", csrfToken);
    setUpDeletePasskeyButtons(".delete-passkey-btn", csrfToken);
  } catch (error) {
    console.error("Error setting up passkey functionality:", error);
  }

  const flashMessage = document.getElementById("flash-message");
  if (flashMessage) {
    const flashMessages = {
      success: flashMessage.dataset.success,
      info: flashMessage.dataset.info,
      error: flashMessage.dataset.error,
    };
    displayFlashMessages(flashMessages);
  }
});
