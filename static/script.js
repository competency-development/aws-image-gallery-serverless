$(document).ready(function () {
  $("#home").attr("href", "/");

  let allImages = [];
  let imagesToDisplay = []

  get("https://uw47difk8j.execute-api.eu-central-1.amazonaws.com/images", function (data) {
    data.forEach((image) => {
      const imageObject = {
        url: image.url,
        description: image.description,
        key: image.key
      };
      allImages.push(imageObject);
      imagesToDisplay.push(imageObject);
    });
    loadMore(imagesToDisplay);
    checkIfLoadMoreRequired();
  });

  function get(url, callback) {
    fetch(url, {
      method: "GET",
      headers: {
        Accept: "application/json, text/plain",
      },
    })
      .then((response) => response.json())
      .then((data) => callback(data))
      .catch((error) => {
        showErrorNotification(error.message);
      });
  }

  const MAX_PAGE_SIZE = 6;

  function loadMore(images) {
    let template = $("#image-card-template").html();
    for (let i = 0; i < MAX_PAGE_SIZE; i++) {
      let imageData = images.shift();
      if (imageData) {
        $(".row").append(Mustache.render(template, imageData));
      }
    }
    bindAllActions();
  }

  function bindAllActions() {
    $(".image").unbind("click").on("click", bindExpandedImage);
    $(".info-button").unbind("click").on("click", showDescription);
    $(".play-button").unbind("click").on("click", openPlayer);
    $(".delete-button").unbind("click").on("click", deleteImage);

    hideAppearingElements();
    bindForMobileDevices();
  }

  function showDescription() {
    const parent = $(this).closest(".image-card");
    const dataToSend = parent[0] ? parent[0].id : '';
    post('https://uw47difk8j.execute-api.eu-central-1.amazonaws.com/recognize', "application/x-www-form-urlencoded", dataToSend, function (data) {
      setTextToDescription(parent, data);
    });
  }

  function setTextToDescription(parent, data) {
    parent.find(".hover-buttons").toggle();
    const description = parent.find(".description");
    description.addClass("appear-animation").toggle();
    description.find('.description-text').text(data.replaceAll(", ", " "));
  }

  function post(url, contentType, dataToSend, callback) {
    const fetchOptions = {
      method: "POST",
      body: dataToSend, // in comparison with Spring server, where we send with FormData - we are sending the file/object directly with serverless implementation
      headers: {
        "Content-type": contentType
      }
    };
    fetch(url, fetchOptions)
      .then((response) => response.json())
      .then((data) => callback(data))
      .catch((error) => {
        showErrorNotification(error.message);
      });
  }

  function openPlayer() {
    const parent = $(this).closest(".image-card");
    const dataToSend = parent[0] ? parent[0].id : '';
    post('https://uw47difk8j.execute-api.eu-central-1.amazonaws.com/recognize', "application/x-www-form-urlencoded", dataToSend, function (data) {
      post('https://uw47difk8j.execute-api.eu-central-1.amazonaws.com/synthesize', "application/x-www-form-urlencoded", data, function (audio) {
        parent.find(".hover-buttons").toggle();
        parent.find('.audio').addClass('appear-animation').append('<audio id="audio" src="' + audio + '" controls autoplay></audio>').toggle();
      });
    });
  }

  function bindExpandedImage() {
    const parent = $(this).closest("img");
    $("#expanded-image").attr("src", parent.attr("src"));
  }

  function hideAppearingElements() {
    $(".description").hide();
    $(".audio").empty().hide();
    $(".hover-buttons").show();
  }

  // Show hover buttons for mobile devices
  function bindForMobileDevices() {
    if ($(window).width() < 992) {
      $(".hover-buttons").css("opacity", "1");
      $(".description, .audio").on("click", hideAppearingElements);
    }
  }

  function checkIfLoadMoreRequired() {
    if (imagesToDisplay.length > 0) {
      $("#load-more").show();
    } else {
      $("#load-more").hide();
    }
  }

  // Clear description on click outside or "Info" button click
  $(document).on("mouseover", function (event) {
    if (!$(event.target).closest(".image-card").length) {
      hideAppearingElements();
    }
  });

  $("#upload-file").on("click", function (event) {
    $("#upload-file-input").trigger("click");
    event.preventDefault();
  });

  $("#upload-file-input").on("change", function () {
    const file = $(this).prop("files")[0];
    const url = "https://uw47difk8j.execute-api.eu-central-1.amazonaws.com/images/upload";
    post(url, "multipart/form-data", file, function (data) {
      showSuccessUploadNotification(file.name);
      const template = $("#image-card-template").html();
      $(".row").prepend(Mustache.render(template, data));
      bindAllActions();
    });
  });

  function deleteImage() {
    const parent = $(this).closest(".image-card");
    const dataToSend = parent[0] ? parent[0].id : '';
    post("https://uw47difk8j.execute-api.eu-central-1.amazonaws.com/images/delete", "multipart/form-data", dataToSend, function (data) {
      parent.addClass("delete-animation");
      setTimeout(function () {
        parent.remove();
        showSuccessDeleteNotification(data);
      }, 500);
    });
  }

  function showSuccessUploadNotification(fileNameText) {
    const templateData = { fileName: fileNameText };
    const alertTemplate = $("#upload-alert-template").html();
    $(".header").prepend(Mustache.render(alertTemplate, templateData));
  }

  function showSuccessDeleteNotification(fileNameText) {
    const templateData = { fileName: fileNameText };
    const alertTemplate = $("#delete-alert-template").html();
    $(".header").prepend(Mustache.render(alertTemplate, templateData));
  }

  function showErrorNotification(errorMessage) {
    const templateData = { message: errorMessage };
    const alertTemplate = $("#error-alert-template").html();
    $(".header").prepend(Mustache.render(alertTemplate, templateData));
  }

  $("#load-more").on("click", function (event) {
    event.preventDefault();
    loadMore(imagesToDisplay);
    checkIfLoadMoreRequired();
  });

});