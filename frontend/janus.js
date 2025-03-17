// import jQuery from 'jquery';
import 'materialize-css';
import { Datepicker } from 'materialize-css';

document.addEventListener('DOMContentLoaded', function() {
    "use strict";
    //Initialise Materialize elements
    const sidenavElems = document.querySelectorAll('.sidenav');
    const sidenavInstances = M.Sidenav.init(sidenavElems);
    const modalElems = document.querySelectorAll('.modal');
    const modalInstances = M.Modal.init(modalElems);
    M.updateTextFields();
    const collapsibleElems = document.querySelectorAll('.collapsible');
    const collapsibleInstances = M.Collapsible.init(collapsibleElems);
    const dropdownElems = document.querySelectorAll('.dropdown-trigger');
    const dropdownInstances = M.Dropdown.init(dropdownElems);  
    const tooltipElems = document.querySelectorAll('.tooltipped');
    const instances = M.Tooltip.init(tooltipElems);

    // aws-profile-name
    if (document.querySelectorAll('.editable-aws-profile').length) {
        const profileIdContainers = document.querySelectorAll('.editable-aws-profile .aws-profile-id');

        document.getElementById('aws-profile-id').addEventListener('keyup', function() {
            const input = this;
            profileIdContainers.forEach(function(el) {
                let lines;
                if (el.tagName === "TEXTAREA") {
                    lines = el.value.split("\n");
                } else {
                    lines = el.innerText.split("\n");
                }
                const replaced = lines.map(function(line) {
                    return line.replace(/--profile [a-zA-Z0-9\-]*/, "--profile " + input.value);
                });
                if (el.tagName === "TEXTAREA") {
                    el.value = replaced.join("\n");
                } else {
                    el.innerText = replaced.join("\n");
                }
            });
        });
    }

    // copy-text
    document.querySelectorAll(".copy-text--button").forEach(function(button) {
        const container = button.closest(".copy-textarea"),
            target = container.querySelector("textarea"),
            defaultIcon = container.querySelector(".copy-text--default"),
            confirmationIcon = container.querySelector(".copy-text--confirm"),
            warnIcon = container.querySelector(".copy-text--warn");

        button.addEventListener('click', function(e) {
            e.preventDefault();
            try {
                target.select();
                document.execCommand('copy'); // we should replace this with the clipboard function below, see https://developer.mozilla.org/en-US/docs/Web/API/Document/execCommand
                // navigator.clipboard.writeText(target.value); // only works in secure environments, see https://developer.mozilla.org/en-US/docs/Web/API/Navigator/clipboard
                confirmationIcon.style.display = "inline";
                defaultIcon.style.display = "none";
                setTimeout(function() {
                    confirmationIcon.style.display = "none";
                    warnIcon.style.display = "none";
                    defaultIcon.style.display = "inline";
                }, 4000);
            } catch (err) {
                warnIcon.style.display = "inline";
                defaultIcon.style.display = "none";
                throw(err);
            }
        });
    });

    // index page controls are fixed until user scrolls to footer
    document.querySelectorAll('.controls__hero').forEach(function(container) {
        const win = window,
            controlContainer = document.querySelector('.controls__hero'),
            footer = document.querySelector('footer'),
            recalculate = function() {
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

        win.addEventListener('scroll', recalculate);
        win.addEventListener('resize', recalculate);
        recalculate();
    });

    // allow user to simultaneously obtain credentials from multiple accounts
    document.querySelectorAll(".multiple-credentials-control__container").forEach(function(container) {
        const checkboxes = document.querySelectorAll(".multi-select__checkbox"),
            singleCredentialsLinks = document.querySelectorAll(".federation__link--credentials"),
            activeContainer = document.querySelector(".multiple-credentials-control--active"),
            inactiveContainer = document.querySelector(".multiple-credentials-control--inactive"),
            controlFeedbackText = document.querySelector(".multi-accounts-count"),
            accountContainers = document.querySelectorAll(".card--aws-account"),
            clearButton = document.querySelector(".multiple-credentials-control--clear"),
            updateLink = function(permissions) {
                const link = document.querySelector(".multiple-credentials__link"),
                    hasPermissions = link.href.indexOf("permissionIds=") !== -1,
                    permissionStr = permissions.join(",");
                if (hasPermissions) {
                    link.href = link.href.replace(/permissionIds=[^&]*/, "permissionIds=" + permissionStr);
                } else {
                    link.href += "&permissionIds=" + permissionStr;
                }
            },
            updateSelection = function(event) {
                const checked = Array.from(checkboxes).filter(function(checkbox) {
                        return checkbox.checked;
                    }),
                    permissions = checked.map(function(checkbox) {
                        return checkbox.getAttribute("data-permission-id");
                    }),
                    clicked = event ? event.target : null,
                    accountContainer = clicked ? clicked.closest(".aws-account-body") : null,
                    otherAccountCheckboxes = accountContainer ? accountContainer.querySelectorAll(".multi-select__checkbox:not(:checked)") : [];

                // enable feature if JS is available
                document.querySelectorAll(".multi-select__container").forEach(el => el.style.display = "block");

                // deal with disabling other permissions in the same account as the one just selected
                if (event) {
                    if (clicked.checked) {
                        otherAccountCheckboxes.forEach(function(checkbox) {
                            checkbox.checked = false;
                            checkbox.disabled = true;
                        });
                    } else {
                        otherAccountCheckboxes.forEach(function(checkbox) {
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
                    singleCredentialsLinks.forEach(function(link) {
                        link.disabled = true;
                    });
                    accountContainers.forEach(function(container) {
                        container.style.filter = "grayscale(0)";
                    });
                    Array.from(accountContainers).filter(function(container) {
                        return !checked.some(function(checkbox) {
                            return container.contains(checkbox);
                        });
                    }).forEach(function(container) {
                        container.style.filter = "grayscale(0.6)";
                    });
                } else {
                    inactiveContainer.style.display = "block";
                    activeContainer.style.display = "none";
                    // restore single credentials links
                    singleCredentialsLinks.forEach(function(link) {
                        link.disabled = false;
                    });
                    accountContainers.forEach(function(container) {
                        container.style.filter = "grayscale(0)";
                    });
                }
                controlFeedbackText.textContent = permissions.length === 1 ? permissions.length + " account" : permissions.length + " accounts";
                updateLink(permissions);
            };

        container.style.display = "block";
        checkboxes.forEach(function(checkbox) {
            checkbox.addEventListener('change', updateSelection);
        });
        clearButton.addEventListener('click', function() {
            checkboxes.forEach(function(checkbox) {
                checkbox.checked = false;
                checkbox.disabled = false;
            });
            updateSelection();
        });
        updateSelection();
    });
    // add timezone to federation links
    document.querySelectorAll(".federation__link").forEach(function(el) {
        const link = el,
            tzOffset = (new Date().getTimezoneOffset() * -1) / 60;
        link.href = link.href + "&tzOffset=" + tzOffset;
    });
    // login lease time
    document.querySelectorAll(".login-duration__container").forEach(function(container) {
        const defaultLongDurationLink = document.querySelector(".dropdown-time__link--default[data-length=standard]"),
            links = container.querySelectorAll(".time-link"),
            maxLongDuration = document.querySelector(".dropdown-time__link--max[data-length=standard]").dataset.duration,
            endOfWorkSeconds = (function() {
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
            updateHrefDurations = function(duration, shortTerm) {
                const selector = ".federation__link--" + (shortTerm ? "short" : "standard");
                document.querySelectorAll(selector).forEach(function(link) {
                    const hasDuration = link.href.indexOf("duration=") !== -1;
                    if (hasDuration) {
                        link.href = link.href.replace(/duration=[^&]*/, "duration=" + duration);
                    } else {
                        link.href = link.href + "&duration=" + duration;
                    }
                });
            };

        // enable feature if JS is available
        links.forEach(function(link) {
            link.removeAttribute("disabled");
        });

        // add click handlers to time choices 
        document.querySelectorAll(".dropdown-time__link").forEach(function(link) {
            link.addEventListener('click', function(e) {
                e.preventDefault();
                // update hrefs
                updateHrefDurations(link.dataset.duration, "short" === link.dataset.length);
                // update button text
                link.closest(".login-duration__header").querySelector(".dropdown-trigger").textContent = link.textContent;
            });
        });

        // remove wallclock option if we're too far from 19:00
        if (msToEndOfWork > maxLongDuration) {
            const walltimeSelector = document.querySelector(".dropdown-time__link--walltime[data-length=standard]");
            walltimeSelector.closest(".login-duration__header").querySelector(".dropdown-trigger").textContent = defaultLongDurationLink.textContent;
            walltimeSelector.remove();
        }

        // remove admin control if there are no admin permissions available
        if (document.querySelectorAll(".federation__link--short").length === 0) {
            document.querySelector(".login-duration--admin").style.display = "none";
        }
    });

    // local times
    document.querySelectorAll(".local-date").forEach(function(el) {
        const dateSpan = el,
            datestamp = dateSpan.getAttribute("data-date"),
            pad = function(n, width, char) {
                char = char || '0';
                n = String(n);
                return n.length >= width ? n : new Array(width - n.length + 1).join(char) + n;
            };
        if (datestamp) {
            const d = new Date(Date.parse(datestamp));
            dateSpan.textContent = pad(d.getHours(), 2) + ":" + pad(d.getMinutes(), 2) + ":" + pad(d.getSeconds(), 2);
        }
    });
    // adjust for windows OS
    document.querySelectorAll(".textarea--code.aws-profile-id").forEach(function(el) {
        if (navigator.userAgent.includes("Win")) { // TODO: test this change in Windows
            const winCmd = el.value.replace(/\\\n/g, "^\n").replace(/^ /mg, "");
            el.value = winCmd;
        }
    });
    {
        // disable old auto-logout cookie to tidy up
        // this can be removed in the near future, when this cookie will have been cleared out of colleague's browsers
        const COOKIE__AUTO_LOGOUT = "janus_auto_logout";
        document.cookie = `${COOKIE__AUTO_LOGOUT}=; expires=Thu, 01 Jan 1970 12:00:00 UTC; path=/`;
    }
});

// jQuery(function($){
    // "use strict";
    // // aws-profile-name
    // if ($('.editable-aws-profile').length) {
    //     var profileIdContainers = $('.editable-aws-profile .aws-profile-id');

    //     $('#aws-profile-id').on('keyup', function(){
    //         var input = $(this);
    //         profileIdContainers.each(function(){
    //             var el = $(this),
    //                 lines;
    //             if (el.prop("tagName") === "TEXTAREA") {
    //                 lines = el.val().split("\n");
    //             } else {
    //                 lines = el.get(0).innerText.split("\n");
    //             }
    //             var replaced = $.map(lines, function(line){
    //                 return line.replace(/--profile [a-zA-Z0-9\-]*/, "--profile " + input.val());
    //             });
    //             if (el.prop("tagName") === "TEXTAREA") {
    //                 el.val(replaced.join("\n"));
    //             } else {
    //                 el.get(0).innerText = replaced.join("\n");
    //             }
    //         });
    //     });
    // // }

    // // copy-text
    // $(".copy-text--button").each(function(_, el){
    //     var button = $(el),
    //         container = button.parents(".copy-textarea"),
    //         target = container.find("textarea"),
    //         defaultIcon = container.find(".copy-text--default"),
    //         confirmationIcon = container.find(".copy-text--confirm"),
    //         warnIcon = container.find(".copy-text--warn");
    //     button.on('click', function(e){
    //         e.preventDefault();
    //         try {
    //             target.trigger('select');
    //             document.execCommand('copy'); // we should replace this with the clipboard function below, see https://developer.mozilla.org/en-US/docs/Web/API/Document/execCommand
    //             // navigator.clipboard.writeText(target.value); // only works in secure environments, see https://developer.mozilla.org/en-US/docs/Web/API/Navigator/clipboard
    //             confirmationIcon.css("display", "inline");
    //             defaultIcon.css("display", "none");
    //             setTimeout(function () {
    //                 confirmationIcon.css("display", "none");
    //                 warnIcon.css("display", "none");
    //                 defaultIcon.css("display", "inline");
    //             }, 4000);
    //         } catch (err) {
    //             warnIcon.css("display", "inline");
    //             defaultIcon.css("display", "none");
    //             throw(err);
    //         }
    //     });
    // });

    // // index page controls are fixed until user scrolls to footer
    // $('.controls__hero').each(function(_, el){
    //     var win = $(window),
    //         container = $(el),
    //         controlContainer = $('.controls__hero'),
    //         footer = $("footer"),
    //         recalculate = function() {
    //             var lowestVisiblePixel = win.scrollTop() + win.height(),
    //                 footerPosition = footer.offset().top;

    //             if (lowestVisiblePixel > footerPosition) {
    //                 controlContainer.css("position", "relative");
    //                 footer.css("margin-top", 0);
    //             } else {
    //                 controlContainer.css("position", "fixed");
    //                 footer.css("margin-top", controlContainer.height() + "px");
    //             }
    //         };
    //     // enable feature if JS is available
    //     controlContainer.show();

    //     win.on('scroll', recalculate);
    //     win.on('resize', recalculate);
    //     recalculate();
    // });

    // // allow user to simultaneously obtain credentials from multiple accounts
    // $(".multiple-credentials-control__container").each(function(_, el){
    //     var checkboxes = $(".multi-select__checkbox"),
    //         container = $(el),
    //         singleCredentialsLinks = $(".federation__link--credentials"),
    //         activeContainer = $(".multiple-credentials-control--active"),
    //         inactiveContainer = $(".multiple-credentials-control--inactive"),
    //         controlFeedbackText = $(".multi-accounts-count"),
    //         accountContainers = $(".card--aws-account"),
    //         clearButton = $(".multiple-credentials-control--clear"),
    //         updateLink = function(permissions){
    //             var link = $(".multiple-credentials__link"),
    //                 hasPermissions = link.prop("href").indexOf("permissionIds=") !== -1,
    //                 permissionStr = permissions.join(",");
    //             if (hasPermissions) {
    //                 link.prop("href", link.prop("href").replace(/permissionIds=[^&]*/, "permissionIds=" + permissionStr));
    //             } else {
    //                 link.prop("href", link.prop("href") + "&permissionIds=" + permissionStr);
    //             }
    //         },
    //         updateSelection = function(event){
    //             var checked = checkboxes.filter(":checked"),
    //                 permissions = $.map(checked, function(el){
    //                     return $(el).data("permission-id");
    //                 }),
    //                 clicked = event ? $(event.target) : $([]),
    //                 accountContainer = clicked.parents(".aws-account-body"),
    //                 otherAccountCheckboxes = accountContainer.find(".multi-select__checkbox").not(clicked);

    //             // enable feature if JS is available
    //             $(".multi-select__container").show();

    //             // deal with disabling other permissions in the same account as the one just selected
    //             if (event) {
    //                 if (clicked.is(":checked")) {
    //                     otherAccountCheckboxes
    //                         .prop("checked", false)
    //                         .attr("disabled", "disabled");
    //                 } else {
    //                     otherAccountCheckboxes
    //                         .prop("checked", false)
    //                         .removeAttr("disabled");
    //                 }
    //             }

    //             // update display
    //             if (permissions.length) {
    //                 activeContainer.show(100);
    //                 inactiveContainer.hide();
    //                 // disable all non-multi credentials links to avoid confusion
    //                 singleCredentialsLinks.attr("disabled", "disabled");
    //                 accountContainers.css("filter", "grayscale(0)");
    //                 accountContainers.not(checked.parents(".card--aws-account")).css("filter", "grayscale(0.6)");
    //             } else {
    //                 inactiveContainer.show();
    //                 activeContainer.hide();
    //                 // restore single credentials links
    //                 singleCredentialsLinks.removeAttr("disabled");
    //                 accountContainers.css("filter", "grayscale(0)");
    //             }
    //             if (permissions.length === 1) {
    //                 controlFeedbackText.text(permissions.length + " account");
    //             } else {
    //                 controlFeedbackText.text(permissions.length + " accounts");
    //             }
    //             updateLink(permissions);
    //         };
    //     container.show();
    //     checkboxes.on('change', updateSelection);
    //     clearButton.on('click', function() {
    //         checkboxes
    //             .prop("checked", false)
    //             .removeAttr("disabled");
    //         updateSelection();
    //     });
    //     updateSelection();
    // });

    // // add timezone to federation links
    // $(".federation__link").each(function(_, el){
    //     var link = $(el),
    //         tzOffset = (new Date().getTimezoneOffset() * -1) / 60;
    //     link.prop("href", link.prop("href") + "&tzOffset=" + tzOffset);
    // });

    // // login lease time
    // $(".login-duration__container").each(function(_, el){
    //     var defaultLongDurationLink = $(".dropdown-time__link--default[data-length=standard]"),
    //         container = $(el),
    //         links = container.find(".time-link"),
    //         maxLongDuration = $(".dropdown-time__link--max[data-length=standard]").data("duration"),
    //         endOfWorkSeconds = (function(){
    //             var ms,
    //                 endOfWork = new Date();
    //             endOfWork.setHours(19);
    //             endOfWork.setMinutes(0);
    //             endOfWork.setSeconds(0);
    //             ms = endOfWork.getTime();
    //             if (Date.now() - endOfWork > 0) {
    //                 return ms + 86400 * 1000;
    //             } else {
    //                 return ms;
    //             }
    //         }()),
    //         msToEndOfWork = endOfWorkSeconds - Date.now(),
    //         updateHrefDurations = function(duration, shortTerm) {
    //             var selector = ".federation__link--" + (shortTerm ? "short" : "standard");
    //             $(selector).each(function(_, el){
    //                 var link = $(el),
    //                     hasDuration = link.prop("href").indexOf("duration=") !== -1;
    //                 if (hasDuration) {
    //                     link.prop("href", link.prop("href").replace(/duration=[^&]*/, "duration=" + duration));
    //                 } else {
    //                     link.prop("href", link.prop("href") + "&duration=" + duration);
    //                 }
    //             });
    //         };

    //     // enable feature if JS is available
    //     links.removeAttr("disabled");

    //     // add click handlers to time choices 
    //     $(".dropdown-time__link").each(function(_, el){
    //         var link = $(el);
    //         link.on('click', function(e){
    //             e.preventDefault();
    //             // update hrefs
    //             updateHrefDurations(link.data("duration"), "short" === link.data("length"));
    //             // update button text
    //             link.parents(".login-duration__header").find(".dropdown-trigger").text(link.text());
    //         });
    //     });
    //     // remove wallclock option if we're too far from 19:00
    //     if (msToEndOfWork > maxLongDuration) {
    //         var walltimeSelector = $(".dropdown-time__link--walltime[data-length=standard]");
    //         walltimeSelector
    //             .parents(".login-duration__header").find(".dropdown-trigger")
    //             .text(defaultLongDurationLink.text());
    //         walltimeSelector.remove();
    //     }
    //     // remove admin control if there are no admin permissions available
    //     if ($(".federation__link--short").length === 0) {
    //         $(".login-duration--admin").hide();
    //     }
    // });

    // // local times
    // $(".local-date").each(function(_, el){
    //     var dateSpan = $(el),
    //         datestamp = dateSpan.data("date"),
    //         pad = function(n, width, char) {
    //             char = char || '0';
    //             n = String(n);
    //             return n.length >= width ? n : new Array(width - n.length + 1).join(char) + n;
    //         };
    //     if (datestamp) {
    //         var d = new Date(Date.parse(datestamp));
    //         dateSpan.text(pad(d.getHours(), 2) + ":" + pad(d.getMinutes(), 2) + ":" + pad(d.getSeconds(), 2));
    //     }
    // });

    // //adjust for windows OS
    // $(".textarea--code.aws-profile-id").each(function(_, el){
    //     if (navigator.userAgent.includes("Win")) { // TODO: test this change in Windows
    //         var winCmd = $(el).val().replace(/\\\n/g, "^\n").replace(/^ /mg, "");
    //         $(el).val(winCmd);
    //     }
    // });

    // {
    //     // disable old auto-logout cookie to tidy up
    //     // this can be removed in the near future, when this cookie will have been cleared out of colleague's browsers
    //     const COOKIE__AUTO_LOGOUT = "janus_auto_logout";
    //     document.cookie = `${COOKIE__AUTO_LOGOUT}=; expires=Thu, 01 Jan 1970 12:00:00 UTC; path=/`;
    // }

// });
